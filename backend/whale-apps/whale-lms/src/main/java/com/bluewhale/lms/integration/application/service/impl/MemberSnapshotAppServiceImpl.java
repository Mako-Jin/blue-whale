package com.bluewhale.lms.integration.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.integration.application.service.MemberSnapshotAppService;
import com.bluewhale.lms.integration.application.view.MemberSnapshotView;
import com.bluewhale.lms.integration.application.view.MemberSyncResultView;
import com.bluewhale.lms.integration.domain.gateway.ErpEmployeeGateway;
import com.bluewhale.lms.integration.domain.model.ErpDirectoryPage;
import com.bluewhale.lms.integration.domain.model.ErpEmployeeEntry;
import com.bluewhale.lms.integration.domain.model.MemberSnapshot;
import com.bluewhale.lms.integration.domain.model.MemberSnapshotQuery;
import com.bluewhale.lms.integration.domain.repository.MemberSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 员工只读快照应用服务实现。
 *
 * <p>同步是「按页拉取 + 逐条 upsert」，而不是先清空再全量插入：</p>
 * <ol>
 *   <li>清空重插会丢掉快照主键，而快照 ID 已被通知日志等数据引用；</li>
 *   <li>中途失败时「清了但没插完」会得到一个残缺目录，而 upsert 中途失败
 *       只是少刷新几条，下一次重跑即自愈；</li>
 *   <li>LMS 从不删除快照行，ERP 侧删除员工时只会表现为状态变化或行不再出现，
 *       历史学习记录需要能继续追溯到人。</li>
 * </ol>
 *
 * <p>同步不做事务包套：一次同步可能上千行，长事务会长时间持有行锁；
 * 每行独立写入反而更安全。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberSnapshotAppServiceImpl implements MemberSnapshotAppService {

    /** 同步最多翻页数（防御源头 total 异常导致死循环） */
    private static final int MAX_SYNC_PAGES = 1000;

    /** ERP 内网契约的单页上限 */
    private static final int ERP_PAGE_MAX = 500;

    private final MemberSnapshotRepository memberSnapshotRepository;

    private final ErpEmployeeGateway erpEmployeeGateway;

    private final LmsProperties properties;

    @Override
    public MemberSyncResultView sync(LmsActor actor) {
        assertGate();
        if (!actor.canManageAll()) {
            throw LmsErrorCode.exception(LmsErrorCode.MEMBER_INVALID, "无权同步员工目录");
        }
        LocalDateTime syncedAt = LocalDateTime.now();
        long size = Math.min(Math.max(properties.getMemberSyncPageSize(), 1), ERP_PAGE_MAX);
        long current = 1L;
        long sourceTotal = 0L;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        while (current <= MAX_SYNC_PAGES) {
            ErpDirectoryPage page = erpEmployeeGateway.fetchPage(current, size);
            sourceTotal = page.getTotal();
            if (page.getRecords().isEmpty()) {
                break;
            }
            for (ErpEmployeeEntry entry : page.getRecords()) {
                Boolean outcome = upsert(entry, syncedAt);
                if (outcome == null) {
                    skipped++;
                } else if (outcome) {
                    created++;
                } else {
                    updated++;
                }
            }
            if (!page.hasNext(current, size)) {
                break;
            }
            current++;
        }
        MemberSyncResultView result = new MemberSyncResultView();
        result.setSyncedAt(syncedAt);
        result.setSourceTotal(sourceTotal);
        result.setCreated(created);
        result.setUpdated(updated);
        result.setSkipped(skipped);
        result.setSnapshotTotal(memberSnapshotRepository.countAll());
        log.info("员工快照同步完成: total={}, created={}, updated={}, skipped={}",
                sourceTotal, created, updated, skipped);
        return result;
    }

    @Override
    public PageResult<MemberSnapshotView> page(MemberSnapshotQuery query, LmsActor actor) {
        assertGate();
        if (!actor.canManageAll()) {
            throw LmsErrorCode.exception(LmsErrorCode.MEMBER_INVALID, "无权查看员工快照");
        }
        long current = Math.max(query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize(), 1L), properties.getMemberPageLimit());
        long total = memberSnapshotRepository.count(query);
        List<MemberSnapshotView> views = memberSnapshotRepository.page(query, (current - 1) * size, (int) size)
                .stream().map(MemberSnapshotView::of).toList();
        return PageResult.of(views, total, current, size);
    }

    @Override
    public MemberSnapshotView byUserId(Long userId) {
        assertGate();
        if (userId == null) {
            return null;
        }
        return memberSnapshotRepository.findByUserId(userId).map(MemberSnapshotView::of).orElse(null);
    }

    @Override
    public boolean isReachable(Long userId) {
        if (userId == null) {
            return false;
        }
        return memberSnapshotRepository.findByUserId(userId)
                .map(MemberSnapshot::isUrgeable)
                .orElse(false);
    }

    @Override
    public long countActive() {
        return memberSnapshotRepository.countActive();
    }

    /**
     * 单条 upsert。
     *
     * @param entry 目录条目
     * @param syncedAt 同步时刻
     * @return 新增返回 {@code true}、更新返回 {@code false}、无效行返回 {@code null}
     */
    private Boolean upsert(ErpEmployeeEntry entry, LocalDateTime syncedAt) {
        if (entry.getEmployeeNo() == null || entry.getEmployeeNo().isBlank()) {
            return null;
        }
        MemberSnapshot snapshot = entry.toSnapshot(syncedAt);
        try {
            snapshot.validateForSync(syncedAt);
        } catch (RuntimeException exception) {
            // 单行脏数据不打断整批：跳过并计数，让同步能跑完
            log.warn("员工行校验失败已跳过: employeeNo={}, error={}",
                    entry.getEmployeeNo(), exception.getMessage());
            return null;
        }
        Optional<MemberSnapshot> existing = memberSnapshotRepository.findByEmployeeNo(entry.getEmployeeNo());
        existing.ifPresent(found -> snapshot.setId(found.getId()));
        memberSnapshotRepository.save(snapshot);
        return existing.isEmpty();
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }
}

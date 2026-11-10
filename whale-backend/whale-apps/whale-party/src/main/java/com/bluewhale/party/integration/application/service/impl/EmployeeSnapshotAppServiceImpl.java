package com.bluewhale.party.integration.application.service.impl;

import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.integration.application.service.EmployeeSnapshotAppService;
import com.bluewhale.party.integration.application.view.EmployeeSyncResultView;
import com.bluewhale.party.integration.domain.gateway.ErpEmployeeGateway;
import com.bluewhale.party.integration.domain.model.EmployeeSnapshot;
import com.bluewhale.party.integration.domain.model.ErpDirectoryPage;
import com.bluewhale.party.integration.domain.model.ErpEmployeeEntry;
import com.bluewhale.party.integration.domain.repository.EmployeeSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 员工只读快照应用服务实现。
 *
 * <p>同步是「按页拉取 + 逐条 upsert」：清空重插会丢掉主键并在中途失败时留下残缺目录。
 * 每行独立写入、不包事务——一次同步可能上千行，长事务会长时间持有行锁。</p>
 *
 * <p>源目录为空时拒绝覆盖并报 {@code EMPLOYEE_SYNC_EMPTY}：空目录会让覆盖率分母归零，
 * 把看板从「有数据」变成「全 0」，比保留旧快照更危险。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeSnapshotAppServiceImpl implements EmployeeSnapshotAppService {

    /** 最多翻页数（防御源头 total 异常导致死循环） */
    private static final int MAX_SYNC_PAGES = 1000;

    /** ERP 内网契约的单页上限 */
    private static final int ERP_PAGE_MAX = 500;

    private final EmployeeSnapshotRepository employeeSnapshotRepository;

    private final ErpEmployeeGateway erpEmployeeGateway;

    private final PartyProperties properties;

    @Override
    public EmployeeSyncResultView sync(PartyActor actor) {
        assertGate();
        LocalDateTime syncedAt = LocalDateTime.now();
        long size = Math.min(Math.max(properties.getEmployeeSyncPageSize(), 1), ERP_PAGE_MAX);
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
        long activeTotal = employeeSnapshotRepository.countActive();
        if (sourceTotal == 0 && activeTotal == 0) {
            throw PartyErrorCode.exception(PartyErrorCode.EMPLOYEE_SYNC_EMPTY);
        }
        EmployeeSyncResultView result = new EmployeeSyncResultView();
        result.setSyncedAt(syncedAt);
        result.setSourceTotal(sourceTotal);
        result.setCreated(created);
        result.setUpdated(updated);
        result.setSkipped(skipped);
        result.setActiveTotal(activeTotal);
        log.info("员工快照同步完成: total={}, created={}, updated={}, skipped={}, active={}",
                sourceTotal, created, updated, skipped, activeTotal);
        return result;
    }

    @Override
    public long activeEmployeeCount() {
        return employeeSnapshotRepository.countActive();
    }

    /**
     * 单条 upsert。
     *
     * @param entry 目录条目
     * @param syncedAt 同步时刻
     * @return 新增返回 true、更新返回 false、无效行返回 null
     */
    private Boolean upsert(ErpEmployeeEntry entry, LocalDateTime syncedAt) {
        if (entry.getEmployeeNo() == null || entry.getEmployeeNo().isBlank()) {
            return null;
        }
        EmployeeSnapshot snapshot = new EmployeeSnapshot();
        snapshot.setEmployeeNo(entry.getEmployeeNo().trim());
        snapshot.setUserId(entry.getUserId());
        snapshot.setName(entry.getName());
        snapshot.setOrgCode(entry.getOrgCode());
        snapshot.setPosition(entry.getPosition());
        snapshot.setStatus(entry.getStatus());
        snapshot.setHireDate(entry.getHireDate());
        snapshot.setLeaveDate(entry.getLeaveDate());
        snapshot.setLastSyncedAt(syncedAt);
        Optional<EmployeeSnapshot> existing = employeeSnapshotRepository.findByEmployeeNo(snapshot.getEmployeeNo());
        existing.ifPresent(found -> snapshot.setId(found.getId()));
        try {
            employeeSnapshotRepository.save(snapshot);
        } catch (RuntimeException exception) {
            log.warn("员工行写入失败已跳过: employeeNo={}, error={}",
                    snapshot.getEmployeeNo(), exception.getMessage());
            return null;
        }
        return existing.isEmpty();
    }

    /**
     * 需求门禁。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw PartyErrorCode.exception(PartyErrorCode.GATE_CLOSED);
        }
    }
}

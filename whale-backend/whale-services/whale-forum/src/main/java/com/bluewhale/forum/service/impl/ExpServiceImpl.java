package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ExpItemVO;
import com.bluewhale.forum.dto.ExpSummaryVO;
import com.bluewhale.forum.entity.ForumExpLedger;
import com.bluewhale.forum.mapper.ForumExpLedgerMapper;
import com.bluewhale.forum.service.ExpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 论坛经验账本实现。
 *
 * <p>记账用 {@code INSERT IGNORE}，靠唯一键 {@code (tenant_id, user_id, event, ref_thread_id)}
 * 幂等，不靠捕获重复键异常——异常控制流会把事务标成 rollback-only。
 * 「每帖只计一次」是刻意口径：取消投花再投、撤销精华再题，都不重复加分；
 * 对账成本为零，且天然防刷（同一帖子反复操作刷不出第二份经验）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpServiceImpl implements ExpService {

    private final ForumExpLedgerMapper ledgerMapper;

    private final ForumProperties properties;

    @Override
    public int award(Long userId, String event, int amount, Long threadId) {
        if (userId == null || threadId == null || amount <= 0
                || event == null || event.isBlank()) {
            return 0;
        }
        try {
            ForumExpLedger ledger = new ForumExpLedger();
            ledger.setId(IdWorker.getId());
            ledger.setTenantId(TenantContext.requireId());
            ledger.setUserId(userId);
            ledger.setEvent(event);
            ledger.setAmount(amount);
            ledger.setRefThreadId(threadId);
            int rows = ledgerMapper.insertIgnore(ledger);
            if (rows > 0) {
                log.debug("经验入账 user={} event={} amount={} thread={}", userId, event, amount, threadId);
            }
            return rows;
        } catch (RuntimeException e) {
            // 经验是附属能力：账本故障不能影响发帖/投花/处置主流程
            log.warn("经验记账失败已跳过 user={} event={} thread={} reason={}",
                    userId, event, threadId, e.getMessage());
            return 0;
        }
    }

    @Override
    public long totalOf(Long userId) {
        return userId == null ? 0L : ledgerMapper.sumExp(TenantContext.requireId(), userId);
    }

    @Override
    public ExpSummaryVO myExp(ForumActor actor) {
        Long tenantId = TenantContext.requireId();
        ExpSummaryVO vo = new ExpSummaryVO();
        vo.setTotal(ledgerMapper.sumExp(tenantId, actor.userId()));
        List<ForumExpLedger> rows = ledgerMapper.selectList(new QueryWrapper<ForumExpLedger>()
                .eq("tenant_id", tenantId)
                .eq("user_id", actor.userId())
                .orderByDesc("create_time")
                .orderByDesc("id")
                .last("LIMIT " + Math.max(properties.getExpLedgerPageSize(), 1)));
        vo.setRecent(rows.stream().map(row -> {
            ExpItemVO item = new ExpItemVO();
            item.setEvent(row.getEvent());
            item.setAmount(row.getAmount());
            item.setRefThreadId(String.valueOf(row.getRefThreadId()));
            item.setCreateTime(row.getCreateTime());
            return item;
        }).toList());
        return vo;
    }
}

package com.bluewhale.pay.service.impl;

import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayReconcileDiffMapper;
import com.bluewhale.pay.mapper.PayTransactionMapper;
import com.bluewhale.pay.mapper.stat.PayChannelAmountPo;
import com.bluewhale.pay.mapper.stat.PayDayAmountPo;
import com.bluewhale.pay.mapper.stat.PayPaidStatPo;
import com.bluewhale.pay.mapper.stat.PayPendingStatPo;
import com.bluewhale.pay.service.PayOverviewStatQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link PayOverviewStatQuery} 的 MyBatis 实现。
 *
 * <p>所有查询都显式带 {@code tenant_id}，并且都把时间写成
 * {@code column >= ? AND column < ?}：函数套在条件列上会让索引失效，
 * 7 天窗口在流水表上会从毫秒级变成全表扫。</p>
 *
 * <p>空结果一律折算成 0 而不是 null：概览是给人看的，"今日收款 ¥null"比 0 更像故障。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PayOverviewStatQueryImpl implements PayOverviewStatQuery {

    /** 渠道缺失时的兜底编码（历史数据允许为空） */
    private static final String UNKNOWN_CHANNEL = "UNKNOWN";

    /** 金额零值 */
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PayOrderMapper payOrderMapper;

    private final PayTransactionMapper payTransactionMapper;

    private final PayReconcileDiffMapper diffMapper;

    @Override
    public PaidStat paidBetween(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return new PaidStat(0L, ZERO);
        }
        PayPaidStatPo row = payOrderMapper.sumPaidBetween(tenantId, from, to);
        if (row == null) {
            return new PaidStat(0L, ZERO);
        }
        return new PaidStat(valueOf(row.getPaidOrders()), amountOf(row.getPaidAmount()));
    }

    @Override
    public long createdBetween(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return 0L;
        }
        return payOrderMapper.countCreatedBetween(tenantId, from, to);
    }

    @Override
    public PendingStat pending(Long tenantId, LocalDateTime staleBefore) {
        if (tenantId == null || staleBefore == null) {
            return new PendingStat(0L, 0L);
        }
        PayPendingStatPo row = payOrderMapper.countPending(tenantId, staleBefore);
        if (row == null) {
            return new PendingStat(0L, 0L);
        }
        return new PendingStat(valueOf(row.getPendingOrders()), valueOf(row.getStaleOrders()));
    }

    @Override
    public BigDecimal refundBetween(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return ZERO;
        }
        return amountOf(payTransactionMapper.sumRefundBetween(tenantId, from, to));
    }

    @Override
    public long failedPaymentsBetween(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return 0L;
        }
        return payTransactionMapper.countFailedPayBetween(tenantId, from, to);
    }

    @Override
    public List<DayAmount> paidByDay(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return List.of();
        }
        List<PayDayAmountPo> rows = payOrderMapper.sumPaidByDay(tenantId, from, to);
        List<DayAmount> result = new ArrayList<>(rows.size());
        for (PayDayAmountPo row : rows) {
            if (row.getDay() == null) {
                continue;
            }
            result.add(new DayAmount(row.getDay(), amountOf(row.getAmount())));
        }
        return List.copyOf(result);
    }

    @Override
    public List<ChannelAmount> paidByChannel(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return List.of();
        }
        List<PayChannelAmountPo> rows = payOrderMapper.sumPaidByChannel(tenantId, from, to);
        List<ChannelAmount> result = new ArrayList<>(rows.size());
        for (PayChannelAmountPo row : rows) {
            String channel = row.getChannel();
            result.add(new ChannelAmount(channel == null || channel.isBlank() ? UNKNOWN_CHANNEL : channel,
                    valueOf(row.getOrders()), amountOf(row.getAmount())));
        }
        return List.copyOf(result);
    }

    @Override
    public long openDiffCount(Long tenantId) {
        return tenantId == null ? 0L : diffMapper.countOpen(tenantId);
    }

    @Override
    public long openAmountMismatchCount(Long tenantId) {
        return tenantId == null ? 0L : diffMapper.countOpenAmountMismatch(tenantId);
    }

    /**
     * 时间窗参数是否不可用
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻
     * @param to       截止时刻
     * @return 任一缺失或窗口为空时返回 true
     */
    private boolean invalidWindow(Long tenantId, LocalDateTime from, LocalDateTime to) {
        return tenantId == null || from == null || to == null || !from.isBefore(to);
    }

    /**
     * Long 拆箱兜底
     *
     * @param value 可能为 null 的计数
     * @return 非空计数
     */
    private long valueOf(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 金额兜底
     *
     * @param value 可能为 null 的金额
     * @return 非空金额
     */
    private BigDecimal amountOf(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}

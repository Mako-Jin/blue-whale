package com.bluewhale.pay.service.impl;

import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayReconcileDiffMapper;
import com.bluewhale.pay.mapper.PayTransactionMapper;
import com.bluewhale.pay.mapper.stat.PayChannelAmountPo;
import com.bluewhale.pay.mapper.stat.PayDayAmountPo;
import com.bluewhale.pay.mapper.stat.PayPaidStatPo;
import com.bluewhale.pay.mapper.stat.PayPendingStatPo;
import com.bluewhale.pay.service.PayOverviewStatQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PayOverviewStatQueryImpl} 单元测试。
 *
 * <p>钉住三类口径：① 时间窗参数缺失/倒置一律返回零值且不落库查询；
 * ② 聚合行的 null 计数/金额折算成 0，绝不把 "¥null" 暴露给概览；
 * ③ 历史数据里缺失或空白的渠道码统一并入 UNKNOWN。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PayOverviewStatQueryImplTest {

    private static final long TENANT_ID = 7L;

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 1, 0, 0);

    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 8, 0, 0);

    @Mock
    private PayOrderMapper payOrderMapper;

    @Mock
    private PayTransactionMapper payTransactionMapper;

    @Mock
    private PayReconcileDiffMapper diffMapper;

    @InjectMocks
    private PayOverviewStatQueryImpl query;

    @Test
    @DisplayName("paidBetween：有数据时返回笔数与金额")
    void shouldReturnPaidStat() {
        PayPaidStatPo row = new PayPaidStatPo();
        row.setPaidOrders(12L);
        row.setPaidAmount(new BigDecimal("34560.00"));
        when(payOrderMapper.sumPaidBetween(TENANT_ID, FROM, TO)).thenReturn(row);

        PayOverviewStatQuery.PaidStat stat = query.paidBetween(TENANT_ID, FROM, TO);

        assertEquals(12L, stat.orders());
        assertEquals(0, new BigDecimal("34560.00").compareTo(stat.amount()));
    }

    @Test
    @DisplayName("paidBetween：null 行的计数与金额折算为 0")
    void shouldDefaultNullPaidRowToZero() {
        when(payOrderMapper.sumPaidBetween(TENANT_ID, FROM, TO)).thenReturn(new PayPaidStatPo());

        PayOverviewStatQuery.PaidStat stat = query.paidBetween(TENANT_ID, FROM, TO);

        assertEquals(0L, stat.orders());
        assertEquals(0, BigDecimal.ZERO.compareTo(stat.amount()));
    }

    @Test
    @DisplayName("paidBetween：mapper 无聚合行返回零值")
    void shouldDefaultMissingPaidRowToZero() {
        when(payOrderMapper.sumPaidBetween(TENANT_ID, FROM, TO)).thenReturn(null);

        PayOverviewStatQuery.PaidStat stat = query.paidBetween(TENANT_ID, FROM, TO);

        assertEquals(0L, stat.orders());
        assertEquals(0, BigDecimal.ZERO.compareTo(stat.amount()));
    }

    @Test
    @DisplayName("无效时间窗：租户/起止任一缺失或倒置时所有时间窗查询返回零值且不查库")
    void shouldSkipDbForInvalidWindow() {
        assertEquals(0L, query.createdBetween(null, FROM, TO));
        assertEquals(0L, query.createdBetween(TENANT_ID, null, TO));
        assertEquals(0L, query.createdBetween(TENANT_ID, FROM, null));
        assertEquals(0L, query.createdBetween(TENANT_ID, TO, FROM));

        assertEquals(0L, query.paidBetween(null, FROM, TO).orders());
        assertEquals(0L, query.timedOutClosedBetween(TENANT_ID, TO, FROM));
        assertEquals(0, BigDecimal.ZERO.compareTo(query.refundBetween(null, FROM, TO)));
        assertEquals(0L, query.failedPaymentsBetween(TENANT_ID, FROM, null));
        assertTrue(query.paidByDay(null, FROM, TO).isEmpty());
        assertTrue(query.paidByChannel(TENANT_ID, TO, FROM).isEmpty());

        verify(payOrderMapper, never()).countCreatedBetween(any(), any(), any());
        verify(payOrderMapper, never()).sumPaidByDay(any(), any(), any());
        verify(payOrderMapper, never()).sumPaidByChannel(any(), any(), any());
        verifyNoInteractions(payTransactionMapper);
        verify(payOrderMapper, never()).sumPaidBetween(eq(TENANT_ID), eq(FROM), eq(TO));
    }

    @Test
    @DisplayName("createdBetween：有效时间窗返回建单数")
    void shouldReturnCreatedCount() {
        when(payOrderMapper.countCreatedBetween(TENANT_ID, FROM, TO)).thenReturn(9L);

        assertEquals(9L, query.createdBetween(TENANT_ID, FROM, TO));
    }

    @Test
    @DisplayName("pending：null 租户或 null 阈值返回零值且不查库")
    void shouldDefaultPendingForMissingParams() {
        assertEquals(0L, query.pending(null, FROM).pending());
        assertEquals(0L, query.pending(TENANT_ID, null).stale());
        verify(payOrderMapper, never()).countPending(any(), any());
    }

    @Test
    @DisplayName("pending：null 聚合行返回零值")
    void shouldDefaultMissingPendingRowToZero() {
        when(payOrderMapper.countPending(TENANT_ID, FROM)).thenReturn(null);

        PayOverviewStatQuery.PendingStat stat = query.pending(TENANT_ID, FROM);

        assertEquals(0L, stat.pending());
        assertEquals(0L, stat.stale());
    }

    @Test
    @DisplayName("pending：null 计数折算为 0")
    void shouldDefaultNullPendingNumbersToZero() {
        when(payOrderMapper.countPending(TENANT_ID, FROM)).thenReturn(new PayPendingStatPo());

        PayOverviewStatQuery.PendingStat stat = query.pending(TENANT_ID, FROM);

        assertEquals(0L, stat.pending());
        assertEquals(0L, stat.stale());
    }

    @Test
    @DisplayName("pending：正常返回待支付与超时未支付笔数")
    void shouldReturnPendingStat() {
        PayPendingStatPo row = new PayPendingStatPo();
        row.setPendingOrders(3L);
        row.setStaleOrders(1L);
        when(payOrderMapper.countPending(TENANT_ID, FROM)).thenReturn(row);

        PayOverviewStatQuery.PendingStat stat = query.pending(TENANT_ID, FROM);

        assertEquals(3L, stat.pending());
        assertEquals(1L, stat.stale());
    }

    @Test
    @DisplayName("timedOutClosedBetween / refundBetween / failedPaymentsBetween：有效窗口返回 mapper 结果")
    void shouldReturnTransactionStats() {
        when(payOrderMapper.countTimedOutClosed(TENANT_ID, FROM, TO)).thenReturn(4L);
        when(payTransactionMapper.sumRefundBetween(TENANT_ID, FROM, TO)).thenReturn(new BigDecimal("1200.00"));
        when(payTransactionMapper.countFailedPayBetween(TENANT_ID, FROM, TO)).thenReturn(2L);

        assertEquals(4L, query.timedOutClosedBetween(TENANT_ID, FROM, TO));
        assertEquals(0, new BigDecimal("1200.00").compareTo(query.refundBetween(TENANT_ID, FROM, TO)));
        assertEquals(2L, query.failedPaymentsBetween(TENANT_ID, FROM, TO));
    }

    @Test
    @DisplayName("refundBetween：null 退款金额折算为 0")
    void shouldDefaultNullRefundAmountToZero() {
        when(payTransactionMapper.sumRefundBetween(TENANT_ID, FROM, TO)).thenReturn(null);

        assertEquals(0, BigDecimal.ZERO.compareTo(query.refundBetween(TENANT_ID, FROM, TO)));
    }

    @Test
    @DisplayName("paidByDay：跳过 null 日期行，null 金额折算为 0")
    void shouldSkipNullDayAndDefaultNullAmount() {
        PayDayAmountPo nullDay = new PayDayAmountPo();
        nullDay.setDay(null);
        nullDay.setAmount(new BigDecimal("10.00"));
        PayDayAmountPo nullAmount = new PayDayAmountPo();
        nullAmount.setDay(LocalDate.of(2026, 11, 2));
        nullAmount.setAmount(null);
        when(payOrderMapper.sumPaidByDay(TENANT_ID, FROM, TO)).thenReturn(List.of(nullDay, nullAmount));

        List<PayOverviewStatQuery.DayAmount> result = query.paidByDay(TENANT_ID, FROM, TO);

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2026, 11, 2), result.get(0).day());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.get(0).amount()));
    }

    @Test
    @DisplayName("paidByDay：正常返回按天聚合，null 金额按 0 处理")
    void shouldReturnPaidByDay() {
        PayDayAmountPo row = new PayDayAmountPo();
        row.setDay(LocalDate.of(2026, 11, 3));
        row.setAmount(new BigDecimal("88.00"));
        when(payOrderMapper.sumPaidByDay(TENANT_ID, FROM, TO)).thenReturn(List.of(row));

        List<PayOverviewStatQuery.DayAmount> result = query.paidByDay(TENANT_ID, FROM, TO);

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("88.00").compareTo(result.get(0).amount()));
    }

    @Test
    @DisplayName("paidByChannel：空白/null 渠道并入 UNKNOWN，null 计数与金额折算为 0")
    void shouldNormalizeChannelAndDefaultNulls() {
        PayChannelAmountPo blank = new PayChannelAmountPo();
        blank.setChannel("  ");
        blank.setOrders(null);
        blank.setAmount(null);
        PayChannelAmountPo nullChannel = new PayChannelAmountPo();
        nullChannel.setChannel(null);
        nullChannel.setOrders(2L);
        nullChannel.setAmount(new BigDecimal("20.00"));
        PayChannelAmountPo normal = new PayChannelAmountPo();
        normal.setChannel("ALIPAY");
        normal.setOrders(3L);
        normal.setAmount(new BigDecimal("30.00"));
        when(payOrderMapper.sumPaidByChannel(TENANT_ID, FROM, TO))
                .thenReturn(List.of(blank, nullChannel, normal));

        List<PayOverviewStatQuery.ChannelAmount> result = query.paidByChannel(TENANT_ID, FROM, TO);

        assertEquals(3, result.size());
        assertEquals("UNKNOWN", result.get(0).channel());
        assertEquals(0L, result.get(0).orders());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.get(0).amount()));
        assertEquals("UNKNOWN", result.get(1).channel());
        assertEquals(2L, result.get(1).orders());
        assertEquals("ALIPAY", result.get(2).channel());
        assertEquals(0, new BigDecimal("30.00").compareTo(result.get(2).amount()));
    }

    @Test
    @DisplayName("openDiffCount / openAmountMismatchCount：null 租户返回 0，有效租户查库")
    void shouldQueryOpenDiffCounts() {
        assertEquals(0L, query.openDiffCount(null));
        assertEquals(0L, query.openAmountMismatchCount(null));
        verifyNoInteractions(diffMapper);

        when(diffMapper.countOpen(TENANT_ID)).thenReturn(5L);
        when(diffMapper.countOpenAmountMismatch(TENANT_ID)).thenReturn(2L);

        assertEquals(5L, query.openDiffCount(TENANT_ID));
        assertEquals(2L, query.openAmountMismatchCount(TENANT_ID));
    }
}

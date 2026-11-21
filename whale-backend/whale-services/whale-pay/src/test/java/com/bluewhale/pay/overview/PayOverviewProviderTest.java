package com.bluewhale.pay.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.pay.service.PayOverviewStatQuery;
import com.bluewhale.pay.service.PayOverviewStatQuery.ChannelAmount;
import com.bluewhale.pay.service.PayOverviewStatQuery.DayAmount;
import com.bluewhale.pay.service.PayOverviewStatQuery.PaidStat;
import com.bluewhale.pay.service.PayOverviewStatQuery.PendingStat;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link PayOverviewProvider} 单元测试（T5.3）。
 *
 * <p>钉住四件容易悄悄漂移的事：① KPI 顺序与口径（屏风按位置排版，换序没人报错）；
 * ② 趋势缺日必须补 0；③ 收款与退款各自成列、退款不冲减收款；
 * ④ 今日无收款时渠道图不能给空类目（空类目在屏风上画成一条白线，看着像加载失败）。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PayOverviewProviderTest {

    /** 测试租户 */
    private static final long TENANT_ID = 7L;

    @Mock
    private PayOverviewStatQuery statQuery;

    @InjectMocks
    private PayOverviewProvider provider;

    /**
     * 正常形态：四张 KPI（顺序固定）、两张图、一个支付单入口、无告警
     */
    @Test
    @DisplayName("正常：四张 KPI + 两张图 + 支付单入口，无失败无超时不告警")
    void shouldBuildKpisChartsAndLink() {
        stubBaseline(new PaidStat(12L, new BigDecimal("34560.00")), 20L,
                new PendingStat(3L, 0L), new BigDecimal("1200.00"), 0L, List.of(), List.of(
                        new ChannelAmount("ALIPAY", 9L, new BigDecimal("24560.00")),
                        new ChannelAmount("WECHAT", 3L, new BigDecimal("10000.00"))));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:pay:list")));

        assertEquals("pay", snapshot.service());
        assertEquals("duzhi-hall", snapshot.nodeKey());
        assertEquals(List.of("paid", "paidOrders", "pending", "refund"),
                snapshot.kpis().stream().map(OverviewKpi::key).toList());
        assertEquals(34560d, kpi(snapshot, "paid"));
        assertEquals(12d, kpi(snapshot, "paidOrders"));
        assertEquals(3d, kpi(snapshot, "pending"));
        assertEquals(1200d, kpi(snapshot, "refund"));
        assertEquals(2, snapshot.charts().size());
        assertEquals(1, snapshot.links().size());
        assertEquals("pay-orders", snapshot.links().get(0).key());
        assertEquals("/pay/order/page", snapshot.links().get(0).path());
        assertTrue(snapshot.alerts().isEmpty());
    }

    /**
     * 退款不冲减收款：收款 1000、退款 800 时 KPI 仍是 1000 与 800
     */
    @Test
    @DisplayName("退款单列：收款不因退款变小")
    void shouldNotNetRefundAgainstPaid() {
        stubBaseline(new PaidStat(1L, new BigDecimal("1000.00")), 1L,
                new PendingStat(0L, 0L), new BigDecimal("800.00"), 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:pay:list")));

        assertEquals(1000d, kpi(snapshot, "paid"));
        assertEquals(800d, kpi(snapshot, "refund"));
    }

    /**
     * 趋势序列：近 7 天、缺日补 0、今天在最后一位
     */
    @Test
    @DisplayName("趋势：近 7 天序列缺日补 0，今天在最后一位")
    void shouldZeroFillMissingDays() {
        LocalDate today = LocalDate.now();
        stubBaseline(new PaidStat(0L, BigDecimal.ZERO), 0L, new PendingStat(0L, 0L),
                BigDecimal.ZERO, 0L,
                List.of(new DayAmount(today, new BigDecimal("88.00")),
                        new DayAmount(today.minusDays(6), new BigDecimal("12.00"))),
                List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:pay:list")));

        List<Double> values = snapshot.charts().get(0).series().get(0).values();
        assertEquals(7, values.size());
        assertEquals(List.of(12d, 0d, 0d, 0d, 0d, 0d, 88d), values);
    }

    /**
     * 今日无收款：渠道图给一个 0 值占位类目，而不是空类目
     */
    @Test
    @DisplayName("今日无收款：渠道图保留一个占位类目")
    void shouldKeepPlaceholderWhenNoChannel() {
        stubBaseline(new PaidStat(0L, BigDecimal.ZERO), 0L, new PendingStat(0L, 0L),
                BigDecimal.ZERO, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:pay:list")));

        assertEquals(List.of("今日无收款"), snapshot.charts().get(1).categories());
        assertEquals(List.of(0d), snapshot.charts().get(1).series().get(0).values());
    }

    /**
     * 失败告警：今日支付失败 5 笔（阈值含）→ CRITICAL
     */
    @Test
    @DisplayName("告警：今日失败 5 笔 → CRITICAL")
    void shouldAlertOnFailedPayments() {
        stubBaseline(new PaidStat(0L, BigDecimal.ZERO), 0L, new PendingStat(0L, 0L),
                BigDecimal.ZERO, 5L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:pay:list")));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("5"), snapshot.alerts().get(0).text());
    }

    /**
     * 超时告警：超时未支付 10 笔（阈值含）→ WARNING，且与失败告警可以并存
     */
    @Test
    @DisplayName("告警：超时未支付 10 笔 → WARNING（与失败告警可并存）")
    void shouldAlertOnStalePending() {
        stubBaseline(new PaidStat(0L, BigDecimal.ZERO), 0L, new PendingStat(18L, 10L),
                BigDecimal.ZERO, 6L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:pay:list")));

        assertEquals(2, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertEquals(OverviewAlertLevel.WARNING, snapshot.alerts().get(1).level());
        assertTrue(snapshot.alerts().get(1).text().contains("10"), snapshot.alerts().get(1).text());
    }

    /**
     * 无租户上下文：四个数都是 0，但必须留下"为什么是 0"的说明
     */
    @Test
    @DisplayName("无租户上下文：KPI 全 0 并留下口径说明")
    void shouldDegradeWithoutTenant() {
        OverviewSnapshot snapshot = provider.build(OverviewContext.of(1L, null, Set.of("sys:pay:list")));

        assertEquals(0d, kpi(snapshot, "paid"));
        assertEquals(0d, kpi(snapshot, "pending"));
        assertTrue(snapshot.notes().stream().anyMatch(note -> note.contains("未识别租户")));
    }

    /**
     * 无权限码：入口不下发（下发再让前端隐藏等于把入口清单白送）
     */
    @Test
    @DisplayName("无支付单权限：不下发入口")
    void shouldHideLinkWithoutPermission() {
        stubBaseline(new PaidStat(0L, BigDecimal.ZERO), 0L, new PendingStat(0L, 0L),
                BigDecimal.ZERO, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:user:list")));

        assertFalse(snapshot.links().stream().anyMatch(link -> "pay-orders".equals(link.key())));
    }

    /**
     * 造上下文
     *
     * @param permissions 权限码
     * @return 上下文
     */
    private OverviewContext contextWith(Set<String> permissions) {
        return OverviewContext.of(1L, TENANT_ID, permissions);
    }

    /**
     * 桩：六个统计口全部按参数返回
     *
     * @param paid         收款统计
     * @param createdToday 今日建单数
     * @param pending      待支付统计
     * @param refund       今日退款额
     * @param failed       今日支付失败笔数
     * @param trend        按天趋势
     * @param channels     渠道聚合
     */
    private void stubBaseline(PaidStat paid, long createdToday, PendingStat pending, BigDecimal refund,
                              long failed, List<DayAmount> trend, List<ChannelAmount> channels) {
        when(statQuery.paidBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(paid);
        when(statQuery.createdBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(createdToday);
        when(statQuery.pending(anyLong(), any(LocalDateTime.class))).thenReturn(pending);
        when(statQuery.refundBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(refund);
        when(statQuery.failedPaymentsBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(failed);
        when(statQuery.paidByDay(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(trend);
        when(statQuery.paidByChannel(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(channels);
    }

    /**
     * 取指标值
     *
     * @param snapshot 快照
     * @param key      指标键
     * @return 数值
     */
    private double kpi(OverviewSnapshot snapshot, String key) {
        return snapshot.kpis().stream().filter(item -> key.equals(item.key()))
                .findFirst().orElseThrow().value();
    }
}

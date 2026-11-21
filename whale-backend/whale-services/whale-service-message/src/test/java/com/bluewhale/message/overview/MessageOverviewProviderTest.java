package com.bluewhale.message.overview;

import com.bluewhale.message.service.MessageOverviewStatQuery;
import com.bluewhale.message.service.MessageOverviewStatQuery.ChannelFail;
import com.bluewhale.message.service.MessageOverviewStatQuery.DayDelivery;
import com.bluewhale.message.service.MessageOverviewStatQuery.DeliveryStat;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link MessageOverviewProvider} 单元测试（T5.8）。
 *
 * <p>钉住五件容易悄悄漂移的事：① KPI 顺序与口径（屏风按位置排版，换序没人报错）；
 * ② 送达率的小样本保护（5 条里失败 1 条就是 80%）；③ 趋势缺日必须补 0、
 * 无失败时柱图不能给空类目（空类目在屏风上画成一条白线）；④ 告警的两条判据——
 * 积压是"收下了没送出"、退信是"地址失效"，各自成立且可并存；
 * ⑤ 无租户上下文时必须说明"为什么全是 0"。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MessageOverviewProviderTest {

    /** 测试租户 */
    private static final long TENANT_ID = 7L;

    /** 概览权限码 */
    private static final String PERMISSION = "sys:message:record:list";

    @Mock
    private MessageOverviewStatQuery statQuery;

    @InjectMocks
    private MessageOverviewProvider provider;

    /**
     * 正常形态：四张 KPI（顺序固定）、两张图、一个记录入口、无告警
     */
    @Test
    @DisplayName("正常：四张 KPI + 两张图 + 记录入口，无积压无退信不告警")
    void shouldBuildKpisChartsAndLink() {
        stubBaseline(120L, new DeliveryStat(200L, 198L, 2L, 0L), 0L, List.of(),
                List.of(new ChannelFail("EMAIL", 2L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals("message", snapshot.service());
        assertEquals("tongzheng-hall", snapshot.nodeKey());
        assertEquals(List.of("today", "rate", "backlog", "bounced"),
                snapshot.kpis().stream().map(OverviewKpi::key).toList());
        assertEquals(120d, kpi(snapshot, "today"));
        assertEquals(0.99d, kpi(snapshot, "rate"));
        assertEquals(0d, kpi(snapshot, "backlog"));
        assertEquals(0d, kpi(snapshot, "bounced"));
        assertEquals(2, snapshot.charts().size());
        assertEquals(1, snapshot.links().size());
        assertEquals("records", snapshot.links().get(0).key());
        assertEquals("/message/v1/record/page", snapshot.links().get(0).path());
        assertTrue(snapshot.alerts().isEmpty());
    }

    /**
     * 送达率：成功 / 总投递，副标把成功与失败分开写
     */
    @Test
    @DisplayName("送达率：99% 且副标写成功/失败条数")
    void shouldShowRateHint() {
        stubBaseline(10L, new DeliveryStat(200L, 198L, 2L, 0L), 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(0.99d, kpi(snapshot, "rate"));
        assertEquals("近 24h · 成功 198 / 失败 2", hint(snapshot, "rate"));
        assertEquals(OverviewTone.FLAT, tone(snapshot, "rate"));
    }

    /**
     * 送达率跌破 90%：KPI 转 WARN（不是告警级别，但一眼能看出不对）
     */
    @Test
    @DisplayName("送达率 80%：KPI 转 WARN")
    void shouldWarnOnLowRate() {
        stubBaseline(10L, new DeliveryStat(100L, 80L, 20L, 0L), 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(0.8d, kpi(snapshot, "rate"));
        assertEquals(OverviewTone.WARN, tone(snapshot, "rate"));
    }

    /**
     * 小样本不谈比率：4 条里失败 1 条（75%）不转 WARN，避免噪音
     */
    @Test
    @DisplayName("小样本：4 条投递即使 75% 也不转 WARN")
    void shouldNotWarnOnSmallSample() {
        stubBaseline(4L, new DeliveryStat(4L, 3L, 1L, 0L), 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(0.75d, kpi(snapshot, "rate"));
        assertEquals(OverviewTone.FLAT, tone(snapshot, "rate"));
    }

    /**
     * 近 24h 无投递：比率按 0 展示，但副标要说清是"没有投递"而不是"全部失败"
     */
    @Test
    @DisplayName("无投递：送达率为 0 且副标写「近 24h 无投递」")
    void shouldExplainEmptyWindow() {
        stubBaseline(0L, new DeliveryStat(0L, 0L, 0L, 0L), 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(0d, kpi(snapshot, "rate"));
        assertEquals("近 24h 无投递", hint(snapshot, "rate"));
        assertEquals(OverviewTone.FLAT, tone(snapshot, "rate"));
    }

    /**
     * 趋势序列：近 7 天、缺日补 0、今天在最后一位
     */
    @Test
    @DisplayName("趋势：近 7 天序列缺日补 0，今天在最后一位")
    void shouldZeroFillMissingDays() {
        LocalDate today = LocalDate.now();
        stubBaseline(9L, new DeliveryStat(9L, 9L, 0L, 0L), 0L,
                List.of(new DayDelivery(today, 9L), new DayDelivery(today.minusDays(6), 3L)),
                List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        List<Double> values = snapshot.charts().get(0).series().get(0).values();
        assertEquals(7, values.size());
        assertEquals(List.of(3d, 0d, 0d, 0d, 0d, 0d, 9d), values);
    }

    /**
     * 近 7 日无失败：柱图给一个占位类目与 0 值
     */
    @Test
    @DisplayName("近 7 日无失败：失败渠道柱图保留一个占位类目")
    void shouldKeepPlaceholderWhenNoFailure() {
        stubBaseline(5L, new DeliveryStat(5L, 5L, 0L, 0L), 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(List.of("近 7 日无失败"), snapshot.charts().get(1).categories());
        assertEquals(List.of(0d), snapshot.charts().get(1).series().get(0).values());
    }

    /**
     * 积压告警：还有消息卡在待投递 → CRITICAL（业务方已认为发送成功）
     */
    @Test
    @DisplayName("告警：积压未发 → CRITICAL（收下了没送出）")
    void shouldAlertOnBacklog() {
        stubBaseline(10L, new DeliveryStat(10L, 10L, 0L, 0L), 3L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("3"), snapshot.alerts().get(0).text());
        assertTrue(snapshot.alerts().get(0).text().contains("业务方"),
                snapshot.alerts().get(0).text());
    }

    /**
     * 退信告警：地址失效，重试不会成功 → WARNING
     */
    @Test
    @DisplayName("告警：退信 → WARNING（重试无用，只能改数据）")
    void shouldAlertOnBounce() {
        stubBaseline(10L, new DeliveryStat(20L, 18L, 2L, 2L), 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.WARNING, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("2"), snapshot.alerts().get(0).text());
        assertTrue(snapshot.alerts().get(0).text().contains("重试"),
                snapshot.alerts().get(0).text());
    }

    /**
     * 两条告警并存：积压与退信根因不同，不能合并成一条
     */
    @Test
    @DisplayName("积压与退信同时成立：两条告警并存且积压在前")
    void shouldKeepBothAlerts() {
        stubBaseline(10L, new DeliveryStat(20L, 17L, 3L, 3L), 5L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(2, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertEquals(OverviewAlertLevel.WARNING, snapshot.alerts().get(1).level());
    }

    /**
     * 无租户上下文：四个数都是 0，但必须留下"为什么是 0"的说明
     *
     * <p>这里刻意不打桩：租户为 null 时口径实现会因为 {@code tenant_id = null} 查不到行，
     * 返回 {@code null} / 0。provider 必须自己把这些 null 兜成 0，
     * 否则一次"没带租户头"的访问会让整个快照组装抛 NPE，屏风直接白屏。</p>
     */
    @Test
    @DisplayName("无租户上下文：KPI 全 0 并留下口径说明")
    void shouldDegradeWithoutTenant() {
        OverviewSnapshot snapshot = provider.build(OverviewContext.of(1L, null, Set.of(PERMISSION)));

        assertEquals(0d, kpi(snapshot, "today"));
        assertEquals(0d, kpi(snapshot, "rate"));
        assertEquals(0d, kpi(snapshot, "backlog"));
        assertEquals(0d, kpi(snapshot, "bounced"));
        assertTrue(snapshot.notes().stream().anyMatch(note -> note.contains("未识别租户")));
        assertTrue(snapshot.alerts().isEmpty());
    }

    /**
     * 无权限码：入口不下发（下发再让前端隐藏等于把入口清单白送）
     */
    @Test
    @DisplayName("无消息记录权限：不下发入口")
    void shouldHideLinkWithoutPermission() {
        stubBaseline(5L, new DeliveryStat(5L, 5L, 0L, 0L), 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:user:list")));

        assertFalse(snapshot.links().stream().anyMatch(link -> "records".equals(link.key())));
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
     * 桩：五个统计口全部按参数返回
     *
     * @param todaySent 今日投递条数
     * @param recent    近 24h 投递统计
     * @param backlog   积压条数
     * @param trend     按天趋势
     * @param failTop   失败渠道 Top
     */
    private void stubBaseline(long todaySent, DeliveryStat recent, long backlog,
                              List<DayDelivery> trend, List<ChannelFail> failTop) {
        when(statQuery.sentCount(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(todaySent);
        when(statQuery.deliveryStat(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(recent);
        when(statQuery.backlog(anyLong(), any(LocalDateTime.class))).thenReturn(backlog);
        when(statQuery.dailyDeliveries(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(trend);
        when(statQuery.failedChannels(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class),
                anyInt())).thenReturn(failTop);
    }

    /**
     * 取指标值
     *
     * @param snapshot 快照
     * @param key      指标键
     * @return 数值
     */
    private double kpi(OverviewSnapshot snapshot, String key) {
        return kpiItem(snapshot, key).value();
    }

    /**
     * 取指标语气
     *
     * @param snapshot 快照
     * @param key      指标键
     * @return 语气
     */
    private OverviewTone tone(OverviewSnapshot snapshot, String key) {
        return kpiItem(snapshot, key).tone();
    }

    /**
     * 取指标副标
     *
     * @param snapshot 快照
     * @param key      指标键
     * @return 副标
     */
    private String hint(OverviewSnapshot snapshot, String key) {
        return kpiItem(snapshot, key).hint();
    }

    /**
     * 取指标对象
     *
     * @param snapshot 快照
     * @param key      指标键
     * @return 指标
     */
    private OverviewKpi kpiItem(OverviewSnapshot snapshot, String key) {
        return snapshot.kpis().stream().filter(item -> key.equals(item.key()))
                .findFirst().orElseThrow();
    }
}

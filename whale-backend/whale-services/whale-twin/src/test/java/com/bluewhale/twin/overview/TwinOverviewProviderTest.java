package com.bluewhale.twin.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlert;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewChart;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewLink;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TwinOverviewProvider} 单元测试：四张牌的口径提示、告警三档语气、
 * 近七日柱图、告警分条与入口登记。
 *
 * @author BlueWhale
 */
class TwinOverviewProviderTest {

    private final TwinOverviewStatsService statsService = mock(TwinOverviewStatsService.class);

    private final TwinOverviewProvider provider = new TwinOverviewProvider(statsService);

    /**
     * 构造一份观星台账。
     *
     * @param scenes     场景总数
     * @param published  已发布场景
     * @param draft      草稿场景
     * @param markers    星标总数
     * @param enabled    启用中的星标
     * @param unresolved 未恢复告警
     * @param critical   其中严重
     * @param unacked    其中未确认
     * @param snapshots  发布快照总数
     * @param recent     近七日发布
     * @param daily      近七日逐日告警
     * @return 统计快照
     */
    private static TwinOverviewStats stats(long scenes, long published, long draft, long markers, long enabled,
            long unresolved, long critical, long unacked, long snapshots, long recent, long... daily) {
        LocalDate today = LocalDate.of(2026, 11, 23);
        List<TwinOverviewStats.DailyAlerts> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(new TwinOverviewStats.DailyAlerts(today.minusDays(6 - i), i < daily.length ? daily[i] : 0L));
        }
        return new TwinOverviewStats(scenes, published, draft, 24L, markers, enabled,
                unresolved, critical, unacked, snapshots, recent, days);
    }

    /**
     * 一屏"平安无事"的账本：两座场景已发布、6 个星标、无告警、上月发过一版。
     *
     * @return 统计快照
     */
    private static TwinOverviewStats quiet() {
        return stats(2L, 2L, 0L, 6L, 6L, 0L, 0L, 0L, 3L, 0L, 0L);
    }

    /**
     * 造一个只带租户的调用上下文（provider 只看 tenantId）。
     *
     * @param tenantId 租户 ID
     * @return 上下文
     */
    private static OverviewContext context(Long tenantId) {
        return OverviewContext.of(null, tenantId, Set.of());
    }

    /**
     * 按 key 索引指标。
     *
     * @param snapshot 快照
     * @return key → 指标
     */
    private static Map<String, OverviewKpi> kpis(OverviewSnapshot snapshot) {
        return snapshot.kpis().stream().collect(Collectors.toMap(OverviewKpi::key, kpi -> kpi));
    }

    /**
     * 取快照里的告警条。
     *
     * @param snapshot 快照
     * @return 告警列表
     */
    private static List<OverviewAlert> alerts(OverviewSnapshot snapshot) {
        return snapshot.alerts();
    }

    /**
     * 按级别筛告警条。
     *
     * @param snapshot 快照
     * @param level    级别
     * @return 该级别的告警
     */
    private static List<OverviewAlert> alertsOf(OverviewSnapshot snapshot, OverviewAlertLevel level) {
        return snapshot.alerts().stream().filter(alert -> alert.level() == level).toList();
    }

    @Test
    @DisplayName("四张牌按顺序：场景 / 星标 / 未恢复告警 / 发布快照")
    void shouldExposeFourKpisInOrder() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        OverviewSnapshot snapshot = provider.build(context(9001L));

        assertThat(snapshot.service()).isEqualTo("twin");
        assertThat(snapshot.nodeKey()).isEqualTo("guanxing-stage");
        assertThat(snapshot.kpis()).extracting(OverviewKpi::key)
                .containsExactly("scenes", "markers", "alerts", "snapshots");
        assertThat(snapshot.kpis()).extracting(OverviewKpi::label)
                .containsExactly("观星场景", "星标", "未恢复告警", "发布快照");
        assertThat(snapshot.kpis().get(0).value()).isEqualTo(2d);
        assertThat(snapshot.kpis().get(1).value()).isEqualTo(6d);
        assertThat(snapshot.kpis().get(3).value()).isEqualTo(3d);
    }

    @Test
    @DisplayName("租户 ID 显式透传给统计服务（provider 线程没有 TenantContext）")
    void shouldPassTenantIdToStats() {
        when(statsService.snapshot(4321L)).thenReturn(quiet());

        provider.build(context(4321L));

        verify(statsService).snapshot(4321L);
    }

    @Test
    @DisplayName("场景提示：一个都没发布时说\"全是草稿\"，不让人以为星盘上有东西")
    void shouldHintDraftOnlyScenes() {
        when(statsService.snapshot(9001L)).thenReturn(stats(3L, 0L, 3L, 4L, 4L, 0L, 0L, 0L, 0L, 0L));

        assertThat(kpis(provider.build(context(9001L))).get("scenes").hint())
                .isEqualTo("全是草稿");
    }

    @Test
    @DisplayName("星标提示：停用的不画到浑天仪上，单独报一行")
    void shouldHintDisabledMarkers() {
        when(statsService.snapshot(9001L)).thenReturn(stats(2L, 2L, 0L, 9L, 6L, 0L, 0L, 0L, 1L, 0L));

        assertThat(kpis(provider.build(context(9001L))).get("markers").hint())
                .isEqualTo("另停用 3");
    }

    @Test
    @DisplayName("告警语气：有严重就红，有未恢复就黄，全清才绿")
    void shouldGradeAlertTone() {
        when(statsService.snapshot(9001L)).thenReturn(
                stats(2L, 2L, 0L, 6L, 6L, 4L, 2L, 4L, 3L, 0L),
                stats(2L, 2L, 0L, 6L, 6L, 4L, 0L, 4L, 3L, 0L),
                quiet());

        assertThat(kpis(provider.build(context(9001L))).get("alerts").tone())
                .isEqualTo(OverviewTone.BAD);
        assertThat(kpis(provider.build(context(9001L))).get("alerts").tone())
                .isEqualTo(OverviewTone.WARN);
        assertThat(kpis(provider.build(context(9001L))).get("alerts").tone())
                .isEqualTo(OverviewTone.GOOD);
    }

    @Test
    @DisplayName("告警提示：严重优先于待确认，待确认优先于\"均已确认\"")
    void shouldPreferMoreSevereAlertHint() {
        when(statsService.snapshot(9001L)).thenReturn(
                stats(2L, 2L, 0L, 6L, 6L, 4L, 2L, 3L, 3L, 0L),
                stats(2L, 2L, 0L, 6L, 6L, 4L, 0L, 3L, 3L, 0L),
                stats(2L, 2L, 0L, 6L, 6L, 4L, 0L, 0L, 3L, 0L));

        assertThat(kpis(provider.build(context(9001L))).get("alerts").hint())
                .isEqualTo("严重 2");
        assertThat(kpis(provider.build(context(9001L))).get("alerts").hint())
                .isEqualTo("待确认 3");
        assertThat(kpis(provider.build(context(9001L))).get("alerts").hint())
                .isEqualTo("均已确认");
    }

    @Test
    @DisplayName("柱图：近七日告警，七根柱、横轴月-日、纵轴数值与统计一致")
    void shouldRenderSevenDayAlertTrend() {
        when(statsService.snapshot(9001L))
                .thenReturn(stats(2L, 2L, 0L, 6L, 6L, 0L, 0L, 0L, 3L, 0L, 0L, 2L, 0L, 1L, 3L, 0L, 1L));

        OverviewSnapshot snapshot = provider.build(context(9001L));

        assertThat(snapshot.charts()).hasSize(1);
        OverviewChart chart = snapshot.charts().get(0);
        assertThat(chart.key()).isEqualTo("alert-trend");
        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).hasSize(7);
        assertThat(chart.categories().get(6)).isEqualTo("11-23");
        assertThat(chart.series()).hasSize(1);
        assertThat(chart.series().get(0).name()).isEqualTo("告警数");
        assertThat(chart.series().get(0).values())
                .containsExactly(0d, 2d, 0d, 1d, 3d, 0d, 1d);
    }

    @Test
    @DisplayName("有严重告警：红条一条，说明\"没人知道\"才是重点")
    void shouldRaiseCriticalAlert() {
        when(statsService.snapshot(9001L)).thenReturn(stats(2L, 2L, 0L, 6L, 6L, 4L, 2L, 4L, 3L, 0L, 2L));

        List<OverviewAlert> critical = alertsOf(provider.build(context(9001L)),
                OverviewAlertLevel.CRITICAL);

        assertThat(critical).hasSize(1);
        assertThat(critical.get(0).text()).contains("2 条严重告警");
    }

    @Test
    @DisplayName("未确认：黄条提醒值班的人可能还不知道")
    void shouldRaiseUnackedWarning() {
        when(statsService.snapshot(9001L)).thenReturn(stats(2L, 2L, 0L, 6L, 6L, 3L, 0L, 2L, 3L, 0L, 1L));

        List<OverviewAlert> warnings = alertsOf(provider.build(context(9001L)),
                OverviewAlertLevel.WARNING);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).text()).contains("2 条告警没有人工确认");
    }

    @Test
    @DisplayName("未恢复但都已确认：降成 INFO，不再当作待办催")
    void shouldDowngradeAckedAlerts() {
        when(statsService.snapshot(9001L)).thenReturn(stats(2L, 2L, 0L, 6L, 6L, 3L, 0L, 0L, 3L, 0L, 1L));

        OverviewSnapshot snapshot = provider.build(context(9001L));
        List<OverviewAlert> warnings = alertsOf(snapshot, OverviewAlertLevel.WARNING);

        assertThat(warnings).isEmpty();
        assertThat(alertsOf(snapshot, OverviewAlertLevel.INFO))
                .anyMatch(alert -> alert.text().contains("3 条告警未恢复，但都已确认"));
    }

    @Test
    @DisplayName("一座场景都没有：INFO 明说\"还空着\"，不装成正常")
    void shouldReportEmptyTwin() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(context(9001L));

        assertThat(alertsOf(snapshot, OverviewAlertLevel.INFO))
                .anyMatch(alert -> alert.text().contains("观星台还空着"));
    }

    @Test
    @DisplayName("有场景但一座没发布：WARNING——改动不会生效，这是孪生最典型的静默失败")
    void shouldWarnWhenNothingPublished() {
        when(statsService.snapshot(9001L)).thenReturn(stats(3L, 0L, 3L, 4L, 4L, 0L, 0L, 0L, 0L, 0L));

        List<OverviewAlert> warnings = alertsOf(provider.build(context(9001L)),
                OverviewAlertLevel.WARNING);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).text()).contains("一座都没发布");
    }

    @Test
    @DisplayName("已发布但星标全停用：INFO——浑天仪只有空盘，不报警")
    void shouldNoticeEmptyOrrery() {
        when(statsService.snapshot(9001L)).thenReturn(stats(2L, 2L, 0L, 5L, 0L, 0L, 0L, 0L, 3L, 0L));

        OverviewSnapshot snapshot = provider.build(context(9001L));

        assertThat(alertsOf(snapshot, OverviewAlertLevel.INFO))
                .anyMatch(alert -> alert.text().contains("浑天仪上只有空盘"));
    }

    @Test
    @DisplayName("近七日无告警且无未恢复：INFO 报个好消息")
    void shouldReportQuietWeek() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        OverviewSnapshot snapshot = provider.build(context(9001L));

        assertThat(alertsOf(snapshot, OverviewAlertLevel.INFO))
                .anyMatch(alert -> alert.text().contains("近七日没有一条告警"));
        assertThat(alertsOf(snapshot, OverviewAlertLevel.CRITICAL)).isEmpty();
        assertThat(alertsOf(snapshot, OverviewAlertLevel.WARNING)).isEmpty();
    }

    @Test
    @DisplayName("入口只登记场景台账：path 与 2D 同路径，权限用 sys:twin:scene")
    void shouldRegisterSceneLedgerLink() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        OverviewSnapshot snapshot = provider.build(context(9001L));

        assertThat(snapshot.links()).hasSize(1);
        OverviewLink link = snapshot.links().get(0);
        assertThat(link.key()).isEqualTo("twin-scenes");
        assertThat(link.label()).isEqualTo("场景台账");
        assertThat(link.path()).isEqualTo("/twin/v1/scenes/page");
        assertThat(link.permission()).isEqualTo("sys:twin:scene");
    }

    @Test
    @DisplayName("横轴标签与序列等长：多给一天或少给一天都不许（SDK 会把错位图整张丢掉）")
    void trendCategoriesAndValuesStayAligned() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        OverviewChart chart = provider.build(context(9001L)).charts().get(0);

        assertThat(chart.categories()).hasSameSizeAs(chart.series().get(0).values());
        assertThat(IntStream.range(0, 7).mapToObj(chart.categories()::get).toList())
                .containsExactly("11-17", "11-18", "11-19", "11-20", "11-21", "11-22", "11-23");
    }

    @Test
    @DisplayName("快照提示：近七日发过版就报新增数，没发过就说没有")
    void shouldHintRecentSnapshots() {
        when(statsService.snapshot(9001L)).thenReturn(stats(2L, 2L, 0L, 6L, 6L, 0L, 0L, 0L, 7L, 2L));

        assertThat(kpis(provider.build(context(9001L))).get("snapshots").hint())
                .isEqualTo("近七日 +2");
    }
}

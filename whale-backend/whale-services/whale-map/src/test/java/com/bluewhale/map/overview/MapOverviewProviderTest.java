package com.bluewhale.map.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlert;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewChart;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MapOverviewProvider} 单元测试：四张牌的口径提示、配额语气四档、
 * 近七日调用量柱图、告警分条与空库不误报。
 *
 * @author BlueWhale
 */
class MapOverviewProviderTest {

    private final MapOverviewStatsService statsService = mock(MapOverviewStatsService.class);

    private final MapOverviewProvider provider = new MapOverviewProvider(statsService);

    /**
     * 构造七日用量序列（升序，今天在最后）。
     *
     * @param today  今天
     * @param values 七天的用量，最早在前
     * @return 逐日用量
     */
    private static List<MapOverviewStats.DailyUsage> week(LocalDate today, long... values) {
        List<MapOverviewStats.DailyUsage> days = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            days.add(new MapOverviewStats.DailyUsage(today.minusDays(values.length - 1L - i), values[i]));
        }
        return days;
    }

    /**
     * 一份"平安无事"的账本：有点位、有围栏、缓存都在有效期、今日调用远低于上限。
     *
     * @return 统计快照
     */
    private static MapOverviewStats quiet() {
        return new MapOverviewStats(120L, 6L, 6L, 3L, 3L, 80L, 2L, 120L, 10_000L, 2,
                week(LocalDate.of(2026, 11, 23), 90, 100, 110, 95, 130, 105, 120));
    }

    /**
     * 按 key 索引指标。
     *
     * @param snapshot 快照
     * @return key → 指标
     */
    private static Map<String, OverviewKpi> kpiMap(OverviewSnapshot snapshot) {
        return snapshot.kpis().stream().collect(Collectors.toMap(OverviewKpi::key, kpi -> kpi));
    }

    @Test
    @DisplayName("元信息与权限码：统的是供应商与配额，走运维口径")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("map");
        assertThat(provider.nodeKey()).isEqualTo("yutu-stage");
        assertThat(provider.title()).isEqualTo("舆图司");
        assertThat(provider.subtitle()).isEqualTo("舆图与疆界");
        assertThat(provider.permissionCode()).isEqualTo("sys:map:provider:view");
    }

    @Test
    @DisplayName("租户 ID 从上下文透传给统计服务")
    void shouldPassTenantIdThrough() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));

        assertThat(snapshot.service()).isEqualTo("map");
        assertThat(snapshot.nodeKey()).isEqualTo("yutu-stage");
        assertThat(snapshot.subtitle()).contains("点 · 界 · 注记");
        assertThat(snapshot.notes()).anySatisfy(note -> assertThat(note)
                .contains("80%")
                .contains("上限为 0"));
    }

    @Test
    @DisplayName("上下文为空时不编租户：统计服务收到 null 并自行抛（宁可降级不串数）")
    void shouldPassNullTenantWhenContextMissing() {
        when(statsService.snapshot(null)).thenThrow(new IllegalArgumentException("tenantId is required"));

        assertThatThrownBy(() -> provider.build(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("四张牌：打点/围栏/缓存只报数，围栏提示带停用数")
    void shouldBuildFourKpis() {
        when(statsService.snapshot(9001L)).thenReturn(new MapOverviewStats(120L, 6L, 4L, 3L, 3L, 80L, 2L,
                120L, 10_000L, 2, week(LocalDate.of(2026, 11, 23), 90, 100, 110, 95, 130, 105, 120)));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));

        assertThat(kpis).containsOnlyKeys("locations", "fences", "cache", "calls");
        assertThat(kpis.get("locations").value()).isEqualTo(120.0);
        assertThat(kpis.get("locations").tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(kpis.get("fences").value()).isEqualTo(6.0);
        assertThat(kpis.get("fences").hint()).isEqualTo("另停用 2 个");
        assertThat(kpis.get("cache").value()).isEqualTo(80.0);
        assertThat(kpis.get("cache").hint()).isEqualTo("过期 2 条");
        assertThat(kpis.get("calls").value()).isEqualTo(120.0);
        assertThat(kpis.get("calls").hint()).isEqualTo("上限 10000");
    }

    @Test
    @DisplayName("空库的提示语各自成立：没有打点、没有围栏、缓存还没记")
    void shouldWordEmptyHints() {
        when(statsService.snapshot(9001L)).thenReturn(new MapOverviewStats(0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0, week(LocalDate.of(2026, 11, 23), 0, 0, 0, 0, 0, 0, 0)));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));

        assertThat(kpis.get("locations").hint()).isEqualTo("还没有业务打点");
        assertThat(kpis.get("fences").hint()).isEqualTo("还没划过围栏");
        assertThat(kpis.get("cache").hint()).isEqualTo("问过的地址还没记");
        assertThat(kpis.get("calls").hint()).isEqualTo("供应商未限流");
        assertThat(kpis.get("calls").tone()).isEqualTo(OverviewTone.FLAT);
    }

    @Test
    @DisplayName("配额语气四档：三成绿、八成黄、打满红、不限中性")
    void shouldGradeQuotaTone() {
        LocalDate today = LocalDate.of(2026, 11, 23);
        List<Long> used = List.of(30L, 80L, 100L, 0L);
        List<Long> limit = List.of(100L, 100L, 100L, 0L);
        List<OverviewTone> expected = List.of(OverviewTone.GOOD, OverviewTone.WARN,
                OverviewTone.BAD, OverviewTone.FLAT);
        for (int i = 0; i < used.size(); i++) {
            when(statsService.snapshot(9001L)).thenReturn(new MapOverviewStats(10L, 1L, 1L, 1L, 0L, 0L, 0L,
                    used.get(i), limit.get(i), 1, week(today, 1, 1, 1, 1, 1, 1, used.get(i))));

            Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));

            assertThat(kpis.get("calls").tone()).as("used=%s limit=%s", used.get(i), limit.get(i))
                    .isEqualTo(expected.get(i));
        }
    }

    @Test
    @DisplayName("近七日调用量柱图：七根、标签月-日、口径是调用次数")
    void shouldBuildTrendChart() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));
        OverviewChart chart = snapshot.charts().get(0);

        assertThat(snapshot.charts()).hasSize(1);
        assertThat(chart.key()).isEqualTo("call-trend");
        assertThat(chart.title()).isEqualTo("近七日调用量");
        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.categories()).hasSize(7);
        assertThat(chart.categories().get(6)).isEqualTo("11-23");
        assertThat(chart.series().get(0).name()).isEqualTo("调用次数");
        assertThat(chart.series().get(0).values()).containsExactly(
                90.0, 100.0, 110.0, 95.0, 130.0, 105.0, 120.0);
    }

    @Test
    @DisplayName("入口：围栏台账走围栏查询码，供应商状态走运维码")
    void shouldLinkFenceLedgerAndProviderHealth() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        List<OverviewLink> links = provider.build(OverviewContext.of(7L, 9001L, Set.of())).links();

        assertThat(links).extracting(OverviewLink::key).containsExactly("fences", "providers");
        assertThat(links.get(0).path()).isEqualTo("/map/v1/fence/page");
        assertThat(links.get(0).permission()).isEqualTo("sys:map:fence:query");
        assertThat(links.get(1).path()).isEqualTo("/map/v1/provider/health");
        assertThat(links.get(1).permission()).isEqualTo("sys:map:provider:view");
    }

    @Test
    @DisplayName("配额打满只报一条 CRITICAL，不再重复报吃紧")
    void shouldReportQuotaFullOnce() {
        LocalDate today = LocalDate.of(2026, 11, 23);
        when(statsService.snapshot(9001L)).thenReturn(new MapOverviewStats(10L, 1L, 1L, 1L, 0L, 0L, 0L,
                100L, 100L, 1, week(today, 20, 30, 40, 50, 60, 70, 100)));

        List<OverviewAlert> alerts = provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts();

        assertThat(alerts).filteredOn(alert -> alert.text().contains("配额"))
                .hasSize(1)
                .allSatisfy(alert -> assertThat(alert.level()).isEqualTo(OverviewAlertLevel.CRITICAL));
    }

    @Test
    @DisplayName("配额吃紧报 WARNING，并把已用与上限一起说出来")
    void shouldReportQuotaTight() {
        LocalDate today = LocalDate.of(2026, 11, 23);
        when(statsService.snapshot(9001L)).thenReturn(new MapOverviewStats(10L, 1L, 1L, 1L, 0L, 0L, 0L,
                850L, 1_000L, 2, week(today, 20, 30, 40, 50, 60, 70, 850)));

        List<OverviewAlert> alerts = provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts();

        assertThat(alerts).anySatisfy(alert -> {
            assertThat(alert.level()).isEqualTo(OverviewAlertLevel.WARNING);
            assertThat(alert.text()).contains("850").contains("1000");
        });
    }

    @Test
    @DisplayName("调用量告警：七日全零报停摆，只有今日为零报提示")
    void shouldReportZeroCalls() {
        LocalDate today = LocalDate.of(2026, 11, 23);
        when(statsService.snapshot(9001L)).thenReturn(new MapOverviewStats(10L, 1L, 1L, 1L, 0L, 0L, 0L,
                0L, 0L, 0, week(today, 0, 0, 0, 0, 0, 0, 0)));
        assertThat(provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts())
                .anySatisfy(alert -> assertThat(alert.text()).contains("等于停摆"));

        when(statsService.snapshot(9001L)).thenReturn(new MapOverviewStats(10L, 1L, 1L, 1L, 0L, 0L, 0L,
                0L, 10_000L, 1, week(today, 5, 6, 7, 8, 9, 10, 0)));
        List<OverviewAlert> alerts = provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts();
        assertThat(alerts).anySatisfy(alert -> assertThat(alert.text()).contains("今日还没有人问过地图"));
        assertThat(alerts).noneSatisfy(alert -> assertThat(alert.text()).contains("等于停摆"));
    }

    @Test
    @DisplayName("围栏告警：一条都没有是提示，全部停用是警告")
    void shouldReportFenceStates() {
        LocalDate today = LocalDate.of(2026, 11, 23);
        when(statsService.snapshot(9001L)).thenReturn(new MapOverviewStats(10L, 0L, 0L, 0L, 0L, 0L, 0L,
                5L, 10_000L, 1, week(today, 1, 1, 1, 1, 1, 1, 5)));
        assertThat(provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts())
                .anySatisfy(alert -> {
                    assertThat(alert.level()).isEqualTo(OverviewAlertLevel.INFO);
                    assertThat(alert.text()).contains("还没有划过围栏");
                });

        when(statsService.snapshot(9001L)).thenReturn(new MapOverviewStats(10L, 4L, 0L, 2L, 2L, 0L, 0L,
                5L, 10_000L, 1, week(today, 1, 1, 1, 1, 1, 1, 5)));
        assertThat(provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts())
                .anySatisfy(alert -> {
                    assertThat(alert.level()).isEqualTo(OverviewAlertLevel.WARNING);
                    assertThat(alert.text()).contains("围栏全部停用");
                });
    }

    @Test
    @DisplayName("缓存全部过期才提示：过期不等于要清，但会重新吃配额")
    void shouldReportAllCacheExpired() {
        LocalDate today = LocalDate.of(2026, 11, 23);
        when(statsService.snapshot(9001L)).thenReturn(new MapOverviewStats(10L, 1L, 1L, 1L, 0L, 30L, 30L,
                5L, 10_000L, 1, week(today, 1, 1, 1, 1, 1, 1, 5)));

        assertThat(provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts())
                .anySatisfy(alert -> {
                    assertThat(alert.level()).isEqualTo(OverviewAlertLevel.INFO);
                    assertThat(alert.text()).contains("全部过期");
                });
    }

    @Test
    @DisplayName("空库报一条 INFO；有点位没围栏时不重复报空库")
    void shouldReportEmptyHallOnce() {
        LocalDate today = LocalDate.of(2026, 11, 23);
        when(statsService.snapshot(9001L)).thenReturn(new MapOverviewStats(0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0, week(today, 0, 0, 0, 0, 0, 0, 0)));

        List<OverviewAlert> alerts = provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts();
        assertThat(alerts).filteredOn(alert -> alert.text().contains("空着")).hasSize(1);
        assertThat(alerts).noneSatisfy(alert -> assertThat(alert.text()).contains("还没有划过围栏"));
    }
}

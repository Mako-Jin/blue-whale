package com.bluewhale.pm.overview;

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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PmOverviewProvider} 单元测试：四张牌的口径提示、迭代空档与逾期语气、
 * 类型分布柱图、告警分条。
 *
 * @author BlueWhale
 */
class PmOverviewProviderTest {

    private final PmOverviewStatsService statsService = mock(PmOverviewStatsService.class);

    private final PmOverviewProvider provider = new PmOverviewProvider(statsService);

    /**
     * 构造一份营造账本。
     *
     * @param projectActive   在建项目
     * @param projectArchived 归档项目
     * @param sprintActive    活跃迭代
     * @param sprintFuture    未启动迭代
     * @param sprintClosed    已关闭迭代
     * @param itemTotal       工作项总数
     * @param itemDone        已完成
     * @param itemTodo        待办
     * @param itemInProgress  进行中
     * @param itemOverdue     逾期未完成
     * @param itemOverdueHigh 逾期高优
     * @param itemUnassigned  无主未完成
     * @return 统计快照
     */
    private static PmOverviewStats stats(long projectActive, long projectArchived,
            long sprintActive, long sprintFuture, long sprintClosed,
            long itemTotal, long itemDone, long itemTodo, long itemInProgress,
            long itemOverdue, long itemOverdueHigh, long itemUnassigned) {
        return new PmOverviewStats(projectActive, projectArchived,
                sprintActive, sprintFuture, sprintClosed,
                itemTotal, itemDone, itemTodo, itemInProgress,
                itemOverdue, itemOverdueHigh, itemUnassigned,
                List.of(new PmOverviewStats.TypeCount("史诗", 5),
                        new PmOverviewStats.TypeCount("需求", 20),
                        new PmOverviewStats.TypeCount("任务", 30),
                        new PmOverviewStats.TypeCount("缺陷", 8),
                        new PmOverviewStats.TypeCount("子任务", 2)));
    }

    /**
     * 按 key 索引指标
     *
     * @param snapshot 快照
     * @return key → 指标
     */
    private static Map<String, OverviewKpi> kpiMap(OverviewSnapshot snapshot) {
        return snapshot.kpis().stream().collect(Collectors.toMap(OverviewKpi::key, kpi -> kpi));
    }

    @Test
    @DisplayName("元信息与权限码：复用 2D 项目台账同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("pm");
        assertThat(provider.nodeKey()).isEqualTo("yingzao-bureau");
        assertThat(provider.title()).isEqualTo("营造");
        assertThat(provider.subtitle()).isEqualTo("工程调度");
        assertThat(provider.permissionCode()).isEqualTo("sys:pm:project:list");
    }

    @Test
    @DisplayName("四张牌：各自带口径提示，未完成拆待办与进行中两段")
    void shouldBuildFourKpisWithHints() {
        when(statsService.snapshot()).thenReturn(stats(12L, 4L, 2L, 5L, 30L, 100L, 40L, 35L, 25L, 6L, 2L, 3L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(null));

        assertThat(kpis.get("projects").value()).isEqualTo(12d);
        assertThat(kpis.get("projects").hint()).isEqualTo("归档 4 个");
        assertThat(kpis.get("sprints").hint()).isEqualTo("未启动 5 · 已关闭 30");
        assertThat(kpis.get("items-open").value()).isEqualTo(60d);
        assertThat(kpis.get("items-open").hint()).isEqualTo("待办 35 · 进行中 25");
        assertThat(kpis.get("items-overdue").value()).isEqualTo(6d);
        assertThat(kpis.get("items-overdue").hint()).isEqualTo("高优先级 2");
    }

    @Test
    @DisplayName("迭代空档：有在建项目但 0 个活跃迭代给黄灯，正常运转给中性灯")
    void shouldToneIdleSprints() {
        when(statsService.snapshot()).thenReturn(stats(12L, 4L, 0L, 5L, 30L, 100L, 40L, 35L, 25L, 6L, 2L, 3L));
        assertThat(kpiMap(provider.build(null)).get("sprints").tone()).isEqualTo(OverviewTone.WARN);

        when(statsService.snapshot()).thenReturn(stats(12L, 4L, 2L, 5L, 30L, 100L, 40L, 35L, 25L, 6L, 2L, 3L));
        assertThat(kpiMap(provider.build(null)).get("sprints").tone()).isEqualTo(OverviewTone.FLAT);
    }

    @Test
    @DisplayName("逾期语气：有活拖过期限给红灯，清空了给绿灯")
    void shouldToneOverdueItems() {
        when(statsService.snapshot()).thenReturn(stats(12L, 4L, 2L, 5L, 30L, 100L, 40L, 35L, 25L, 6L, 2L, 3L));
        assertThat(kpiMap(provider.build(null)).get("items-overdue").tone()).isEqualTo(OverviewTone.BAD);

        when(statsService.snapshot()).thenReturn(stats(12L, 4L, 2L, 5L, 30L, 100L, 40L, 35L, 25L, 0L, 0L, 3L));
        assertThat(kpiMap(provider.build(null)).get("items-overdue").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpiMap(provider.build(null)).get("items-overdue").hint()).isEqualTo("没有拖过期限的活");
    }

    @Test
    @DisplayName("类型分布柱图：五种类型按固定顺序，格式是计数")
    void shouldChartTypeDistribution() {
        when(statsService.snapshot()).thenReturn(stats(12L, 4L, 2L, 5L, 30L, 100L, 40L, 35L, 25L, 6L, 2L, 3L));

        OverviewChart chart = provider.build(null).charts().get(0);

        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("史诗", "需求", "任务", "缺陷", "子任务");
        assertThat(chart.series()).hasSize(1);
        assertThat(chart.series().get(0).values()).containsExactly(5d, 20d, 30d, 8d, 2d);
    }

    @Test
    @DisplayName("入口：项目台账，权限码复用 2D 项目列表")
    void shouldLinkProjectPage() {
        when(statsService.snapshot()).thenReturn(stats(12L, 4L, 2L, 5L, 30L, 100L, 40L, 35L, 25L, 6L, 2L, 3L));

        List<OverviewLink> links = provider.build(null).links();

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.path()).isEqualTo("/pm/project/page");
            assertThat(link.permission()).isEqualTo("sys:pm:project:list");
        });
    }

    @Test
    @DisplayName("告警：逾期是红灯、无主与空档迭代是黄灯，归档与未启动是说明")
    void shouldSeparateWarningsFromInfo() {
        when(statsService.snapshot()).thenReturn(stats(12L, 4L, 0L, 5L, 30L, 100L, 40L, 35L, 25L, 6L, 2L, 3L));

        OverviewSnapshot snapshot = provider.build(null);

        List<String> errors = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.CRITICAL)
                .map(OverviewAlert::text)
                .toList();
        List<String> warnings = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .map(OverviewAlert::text)
                .toList();
        List<String> infos = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.INFO)
                .map(OverviewAlert::text)
                .toList();
        assertThat(errors).anyMatch(text -> text.contains("6 件工作项拖过期限"));
        assertThat(warnings).anyMatch(text -> text.contains("3 件未完成的活没派负责人"))
                .anyMatch(text -> text.contains("没有任何进行中的迭代"));
        assertThat(infos).anyMatch(text -> text.contains("5 个迭代还没启动"))
                .anyMatch(text -> text.contains("4 个项目已归档"));
    }

    @Test
    @DisplayName("没有任何项目：四张牌都空但只提示无账可理，不误报逾期与空档")
    void shouldNotBlameEmptyLedgers() {
        when(statsService.snapshot()).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(null);

        assertThat(kpiMap(snapshot).get("projects").hint()).isEqualTo("还没有建项目");
        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING
                        || alert.level() == OverviewAlertLevel.CRITICAL)
                .isEmpty();
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("无账可理"));
    }
}

package com.bluewhale.mes.dashboard.overview;

import com.bluewhale.mes.dashboard.service.MesDashboardService;
import com.bluewhale.mes.dashboard.vo.MesDashboardVO;
import com.bluewhale.mes.dashboard.vo.MesEquipmentBoardVO;
import com.bluewhale.mes.dispatch.vo.MesBoardVO;
import com.bluewhale.mes.quality.vo.MesQualityBoardVO;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlert;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewChart;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewLink;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewState;
import com.bluewhale.overview.model.OverviewTone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MesOverviewProvider} 单元测试：四域口径与 2D 综合看板同源、达成率换算成比率。
 *
 * @author BlueWhale
 */
class MesOverviewProviderTest {

    private final MesDashboardService dashboardService = mock(MesDashboardService.class);

    private final MesOverviewProvider provider = new MesOverviewProvider(dashboardService);

    /**
     * 构造一份有数的看板快照
     *
     * @param ratePercent 达成率（百分数，null 表示今日无计划）
     * @return 综合看板
     */
    private static MesDashboardVO dashboard(BigDecimal ratePercent) {
        MesBoardVO production = new MesBoardVO();
        production.setBoardDate(LocalDate.of(2026, 11, 21));
        production.setTargetQuantity(new BigDecimal("1200"));
        production.setCompletedQuantity(new BigDecimal("1080"));
        production.setDefectQuantity(new BigDecimal("12"));
        production.setAchievementRate(ratePercent);
        production.setExceptionCount(3L);
        production.setActiveTaskCount(5L);

        MesQualityBoardVO quality = new MesQualityBoardVO();
        quality.setNcrOpenCount(2L);
        quality.setSpcAlertCount(1L);

        MesEquipmentBoardVO equipment = new MesEquipmentBoardVO();
        equipment.setTotalEquipmentCount(48L);
        equipment.setFaultEquipmentCount(2L);

        MesDashboardVO dashboard = new MesDashboardVO();
        dashboard.setBoardDate(LocalDate.of(2026, 11, 21));
        dashboard.setProduction(production);
        dashboard.setQuality(quality);
        dashboard.setEquipment(equipment);
        return dashboard;
    }

    /**
     * 具备工单列表权限的调用者
     *
     * @return 上下文
     */
    private static OverviewContext context() {
        return OverviewContext.of(1L, 1L, Set.of("sys:mes:dashboard:view", "sys:mes:workorder:list"));
    }

    @Test
    @DisplayName("元信息与权限码：复用 2D 综合看板同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("mes");
        assertThat(provider.nodeKey()).isEqualTo("gongbu-hall");
        assertThat(provider.title()).isEqualTo("工部");
        assertThat(provider.permissionCode()).isEqualTo("sys:mes:dashboard:view");
    }

    @Test
    @DisplayName("四个 KPI 与综合看板同源：完成 / 达成率 / 未关闭 / 故障设备")
    void shouldMapDashboardToKpis() {
        when(dashboardService.dashboard(null, null, null)).thenReturn(dashboard(new BigDecimal("90.00")));

        OverviewSnapshot snapshot = provider.build(context());
        Map<String, OverviewKpi> byKey = snapshot.kpis().stream()
                .collect(Collectors.toMap(OverviewKpi::key, kpi -> kpi));

        assertThat(byKey).containsOnlyKeys("completed", "achievement", "exceptions", "faults");
        assertThat(byKey.get("completed").value()).isEqualTo(1080d);
        // 未关闭 = 生产异常 3 + NCR 2，合成一个数而不是两个牌面
        assertThat(byKey.get("exceptions").value()).isEqualTo(5d);
        assertThat(byKey.get("exceptions").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(byKey.get("faults").value()).isEqualTo(2d);
        assertThat(byKey.get("faults").tone()).isEqualTo(OverviewTone.BAD);
        assertThat(snapshot.state()).isEqualTo(OverviewState.LIVE);
        assertThat(snapshot.subtitle()).contains("2026-11-21");
    }

    @Test
    @DisplayName("达成率换算成比率：90.00 百分数 → 0.9，不是 90")
    void shouldConvertAchievementRateToRatio() {
        when(dashboardService.dashboard(null, null, null)).thenReturn(dashboard(new BigDecimal("90.00")));

        OverviewSnapshot snapshot = provider.build(context());
        OverviewKpi rate = snapshot.kpis().stream()
                .filter(kpi -> "achievement".equals(kpi.key()))
                .findFirst()
                .orElseThrow();

        assertThat(rate.value()).isEqualTo(0.9d);
        assertThat(rate.format()).isEqualTo(OverviewFormat.PERCENT);
        assertThat(rate.tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(rate.hint()).isEqualTo("目标 1200");
    }

    @Test
    @DisplayName("达成率语气：95% 及以上为良好，80% 以下必须有人看")
    void shouldGradeAchievementRate() {
        when(dashboardService.dashboard(null, null, null)).thenReturn(dashboard(new BigDecimal("95.00")));
        assertThat(rateOf(provider.build(context()))).isEqualTo(OverviewTone.GOOD);

        when(dashboardService.dashboard(null, null, null)).thenReturn(dashboard(new BigDecimal("79.99")));
        assertThat(rateOf(provider.build(context()))).isEqualTo(OverviewTone.WARN);
    }

    @Test
    @DisplayName("今日无计划：达成率不判好坏，提示语写清\"无计划\"而不是显示 0%")
    void shouldStayNeutralWithoutPlan() {
        when(dashboardService.dashboard(null, null, null)).thenReturn(dashboard(null));

        OverviewSnapshot snapshot = provider.build(context());
        OverviewKpi rate = snapshot.kpis().stream()
                .filter(kpi -> "achievement".equals(kpi.key()))
                .findFirst()
                .orElseThrow();

        assertThat(rate.value()).isZero();
        assertThat(rate.tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(rate.hint()).isEqualTo("今日无计划");
    }

    @Test
    @DisplayName("空字段兜底：域为空时不抛，图表补 0 而不是 NaN")
    void shouldTolerateMissingDomains() {
        MesDashboardVO empty = new MesDashboardVO();
        when(dashboardService.dashboard(null, null, null)).thenReturn(empty);

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.kpis()).extracting(OverviewKpi::key)
                .containsExactly("completed", "achievement", "exceptions", "faults");
        assertThat(snapshot.subtitle()).contains("今日");
        assertThat(snapshot.alerts()).isEmpty();
        assertThat(snapshot.charts().get(0).series().get(0).values()).containsExactly(0d, 0d, 0d);
    }

    @Test
    @DisplayName("产量柱状图：目标/完成/不良同源，类别与数值等长且口径为 count")
    void shouldExposeOutputChart() {
        when(dashboardService.dashboard(null, null, null)).thenReturn(dashboard(new BigDecimal("90.00")));

        OverviewSnapshot snapshot = provider.build(context());
        OverviewChart chart = snapshot.charts().get(0);

        assertThat(snapshot.charts()).hasSize(1);
        assertThat(chart.key()).isEqualTo("output");
        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("目标", "完成", "不良");
        assertThat(chart.series().get(0).values()).containsExactly(1200d, 1080d, 12d);
        // 契约要求 categories 与 values 等长；不等长时前端只能自己补 0，图就悄悄画歪了
        assertThat(chart.categories()).hasSize(chart.series().get(0).values().size());
    }

    @Test
    @DisplayName("告警：生产异常与 NCR 合成警告，故障设备升级为严重")
    void shouldRaiseAlerts() {
        when(dashboardService.dashboard(null, null, null)).thenReturn(dashboard(new BigDecimal("90.00")));

        OverviewSnapshot snapshot = provider.build(context());

        // SDK 会按严重度重排告警（critical 在最前），provider 侧只管追加，不管顺序
        assertThat(snapshot.alerts()).extracting(OverviewAlert::level)
                .containsExactly(OverviewAlertLevel.CRITICAL, OverviewAlertLevel.WARNING,
                        OverviewAlertLevel.WARNING, OverviewAlertLevel.INFO);
        assertThat(snapshot.alerts()).extracting(OverviewAlert::count).containsExactly(2, 3, 2, 1);
    }

    @Test
    @DisplayName("生产工单入口：有权限才下发，缺权限时连入口都不出现")
    void shouldTrimLinkByPermission() {
        when(dashboardService.dashboard(null, null, null)).thenReturn(dashboard(new BigDecimal("90.00")));

        OverviewLink link = provider.build(context()).links().get(0);
        assertThat(link.key()).isEqualTo("work-orders");
        assertThat(link.path()).isEqualTo("/mes/v1/work-order/page");
        assertThat(link.permission()).isEqualTo("sys:mes:workorder:list");

        OverviewSnapshot denied = provider.build(OverviewContext.of(1L, 1L, Set.of("sys:mes:dashboard:view")));
        assertThat(denied.links()).isEmpty();
        // 上下文缺失也不能 500：概览宁可少一个入口
        assertThat(provider.build(null).links()).isEmpty();
    }

    /**
     * 取达成率指标的语气
     *
     * @param snapshot 快照
     * @return 语气
     */
    private static OverviewTone rateOf(OverviewSnapshot snapshot) {
        return snapshot.kpis().stream()
                .filter(kpi -> "achievement".equals(kpi.key()))
                .findFirst()
                .orElseThrow()
                .tone();
    }
}

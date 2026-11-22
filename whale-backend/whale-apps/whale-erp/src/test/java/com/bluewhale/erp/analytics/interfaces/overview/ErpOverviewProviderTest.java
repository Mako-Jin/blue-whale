package com.bluewhale.erp.analytics.interfaces.overview;

import com.bluewhale.erp.analytics.application.service.AnalyticsAppService;
import com.bluewhale.erp.analytics.application.view.CockpitView;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ErpOverviewProvider} 单元测试：口径与 2D 驾驶舱同源、语气随预警数变化。
 *
 * @author BlueWhale
 */
class ErpOverviewProviderTest {

    private final AnalyticsAppService analyticsAppService = mock(AnalyticsAppService.class);

    private final ErpOverviewProvider provider = new ErpOverviewProvider(analyticsAppService);

    private static CockpitView cockpit(int alertCount) {
        CockpitView view = new CockpitView();
        view.setSnapshotNo("CPS-202611-0007");
        view.setPeriodCode("202611");
        view.setRevenueAmount(new BigDecimal("1234567.89"));
        view.setCostAmount(new BigDecimal("700000.00"));
        view.setProfitAmount(new BigDecimal("534567.89"));
        view.setOrderCount(4321);
        view.setAlertCount(alertCount);
        return view;
    }

    private static OverviewContext context() {
        return OverviewContext.of(1L, 1L, Set.of("sys:erp:bi:cockpit:list", "sys:erp:purchase:list"));
    }

    @Test
    @DisplayName("元信息与权限码：复用 2D 驾驶舱同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("erp");
        assertThat(provider.nodeKey()).isEqualTo("hubu-hall");
        assertThat(provider.title()).isEqualTo("户部");
        assertThat(provider.permissionCode()).isEqualTo("sys:erp:bi:cockpit:list");
    }

    @Test
    @DisplayName("三个 KPI 与驾驶舱同源：营收/订单/预警")
    void shouldMapCockpitToKpis() {
        when(analyticsAppService.getCockpit(null)).thenReturn(cockpit(0));

        OverviewSnapshot snapshot = provider.build(context());
        Map<String, OverviewKpi> byKey = snapshot.kpis().stream()
                .collect(Collectors.toMap(OverviewKpi::key, kpi -> kpi));

        assertThat(byKey).containsOnlyKeys("revenue", "orders", "alerts");
        assertThat(byKey.get("revenue").value()).isEqualTo(1234567.89d);
        assertThat(byKey.get("revenue").format().getValue()).isEqualTo("money");
        assertThat(byKey.get("orders").value()).isEqualTo(4321d);
        assertThat(byKey.get("alerts").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(snapshot.state()).isEqualTo(OverviewState.LIVE);
        assertThat(snapshot.subtitle()).contains("202611");
        assertThat(snapshot.notes()).anyMatch(note -> note.contains("CPS-202611-0007"));
    }

    @Test
    @DisplayName("有未关闭预警：语气转 warn 并出告警条，条数进 count")
    void shouldRaiseAlertWhenAlertsPresent() {
        when(analyticsAppService.getCockpit(null)).thenReturn(cockpit(3));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.kpis()).anyMatch(kpi -> "alerts".equals(kpi.key()) && kpi.tone() == OverviewTone.WARN);
        assertThat(snapshot.alerts()).extracting(OverviewAlert::level).containsExactly(OverviewAlertLevel.WARNING);
        assertThat(snapshot.alerts().get(0).count()).isEqualTo(3);
    }

    @Test
    @DisplayName("空字段兜底：期间/快照号为空时不往下发 null")
    void shouldTolerateNullFields() {
        CockpitView view = new CockpitView();
        when(analyticsAppService.getCockpit(null)).thenReturn(view);

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.subtitle()).contains("当期");
        assertThat(snapshot.kpis()).extracting(OverviewKpi::key).containsExactly("revenue", "orders", "alerts");
        assertThat(snapshot.alerts()).isEmpty();
        // 图表里的 null 会画成 NaN 柱，整张卡静默变白——必须补 0
        assertThat(snapshot.charts().get(0).series().get(0).values()).containsExactly(0d, 0d, 0d);
    }

    @Test
    @DisplayName("金额柱状图：营收/成本/利润三项同源，口径为 money，不做百分比换算")
    void shouldExposeAmountChart() {
        when(analyticsAppService.getCockpit(null)).thenReturn(cockpit(0));

        OverviewSnapshot snapshot = provider.build(context());
        OverviewChart chart = snapshot.charts().get(0);

        assertThat(snapshot.charts()).hasSize(1);
        assertThat(chart.key()).isEqualTo("pnl");
        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.MONEY);
        assertThat(chart.categories()).containsExactly("营收", "成本", "利润");
        assertThat(chart.series()).hasSize(1);
        assertThat(chart.series().get(0).values()).containsExactly(1234567.89d, 700000d, 534567.89d);
        // 契约要求 categories 与 values 等长；不等长时前端只能自己补 0，图就悄悄画歪了
        assertThat(chart.categories()).hasSize(chart.series().get(0).values().size());
    }

    @Test
    @DisplayName("采购订单入口：有权限才下发，缺权限时连入口都不出现")
    void shouldTrimLinkByPermission() {
        when(analyticsAppService.getCockpit(null)).thenReturn(cockpit(0));

        OverviewSnapshot granted = provider.build(context());
        OverviewLink link = granted.links().get(0);
        assertThat(link.key()).isEqualTo("purchase-orders");
        assertThat(link.path()).isEqualTo("/erp/v1/scm/purchase-order/page");
        assertThat(link.permission()).isEqualTo("sys:erp:purchase:list");

        OverviewSnapshot denied = provider.build(OverviewContext.of(1L, 1L, Set.of("sys:erp:bi:cockpit:list")));
        assertThat(denied.links()).isEmpty();
        // 上下文缺失也不能 500：概览宁可少一个入口
        assertThat(provider.build(null).links()).isEmpty();
    }
}

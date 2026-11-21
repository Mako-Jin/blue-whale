package com.bluewhale.crm.analytics.interfaces.overview;

import com.bluewhale.crm.analytics.application.service.AnalyticsAppService;
import com.bluewhale.crm.analytics.domain.model.DashboardData;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
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
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrmOverviewProvider} 单元测试：口径与 2D 销售看板同源、达成率换算成比率、
 * 数据范围按角色交给 {@link CrmActor}。
 *
 * @author BlueWhale
 */
class CrmOverviewProviderTest {

    private final AnalyticsAppService analyticsAppService = mock(AnalyticsAppService.class);

    private final CrmOverviewProvider provider = new CrmOverviewProvider(analyticsAppService);

    /**
     * 构造一份销售看板
     *
     * @param targetAmount 本月目标金额
     * @param wonAmount 本月赢单金额
     * @param ratePercent 达成率百分数
     * @param riskCount 风险商机数
     * @return 看板数据
     */
    private static DashboardData dashboard(BigDecimal targetAmount, BigDecimal wonAmount,
            BigDecimal ratePercent, long riskCount) {
        DashboardData data = new DashboardData();
        data.setStatDate(LocalDate.of(2026, 11, 21));
        data.setTodayNewLeads(6L);
        data.setWeekWonAmount(new BigDecimal("52000.00"));
        data.setMonthTargetAmount(targetAmount);
        data.setMonthWonAmount(wonAmount);
        data.setTargetCompletionRate(ratePercent);
        data.setWeightedPipelineAmount(new BigDecimal("180000.00"));
        data.setForecastGap(new BigDecimal("260000.00"));
        data.setRiskOpportunityCount(riskCount);
        data.setOpenActionCount(2L);
        return data;
    }

    /**
     * 具备销售看板与客户列表权限的调用者
     *
     * @param roles 角色码
     * @return 上下文
     */
    private static OverviewContext context(String... roles) {
        return OverviewContext.of(1001L, 1L,
                Set.of("sys:crm:analytics:dashboard", "sys:crm:customer:list"), Set.of(roles));
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
    @DisplayName("元信息与权限码：复用 2D 销售看板同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("crm");
        assertThat(provider.nodeKey()).isEqualTo("libu-ritual-hall");
        assertThat(provider.title()).isEqualTo("礼部");
        assertThat(provider.subtitle()).isEqualTo("客户经营");
        assertThat(provider.permissionCode()).isEqualTo("sys:crm:analytics:dashboard");
    }

    @Test
    @DisplayName("达成率：2D 的百分数（94）必须换算成契约比率（0.94），否则屏风显示 9400%")
    void shouldConvertPercentToRatio() {
        when(analyticsAppService.dashboard(any()))
                .thenReturn(dashboard(new BigDecimal("100000.00"), new BigDecimal("94000.00"),
                        new BigDecimal("94.00"), 0L));

        OverviewKpi rate = kpiMap(provider.build(context("crm_sales"))).get("target-rate");

        assertThat(rate.format()).isEqualTo(OverviewFormat.PERCENT);
        assertThat(rate.value()).isEqualTo(0.94d);
        /* 94% 已过 60% 但未满 100%：黄灯，不是绿灯 */
        assertThat(rate.tone()).isEqualTo(OverviewTone.WARN);
    }

    @Test
    @DisplayName("本月没设目标：达成率给灰灯并写明原因，不能让 0% 冒充零业绩")
    void shouldMarkRateFlatWhenNoTarget() {
        when(analyticsAppService.dashboard(any()))
                .thenReturn(dashboard(BigDecimal.ZERO, new BigDecimal("30000.00"),
                        BigDecimal.ZERO, 0L));

        OverviewSnapshot snapshot = provider.build(context("crm_sales"));

        assertThat(kpiMap(snapshot).get("target-rate").tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(kpiMap(snapshot).get("target-rate").hint()).isEqualTo("本月未设目标");
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("未设定销售目标"));
    }

    @Test
    @DisplayName("风险商机：牌面转黄灯，告警条按条数合并成一条")
    void shouldWarnOnRiskOpportunities() {
        when(analyticsAppService.dashboard(any()))
                .thenReturn(dashboard(new BigDecimal("400000.00"), new BigDecimal("120000.00"),
                        new BigDecimal("30.00"), 3L));

        OverviewSnapshot snapshot = provider.build(context("crm_sales"));

        assertThat(kpiMap(snapshot).get("risk").value()).isEqualTo(3d);
        assertThat(kpiMap(snapshot).get("risk").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.text()).contains("3 个商机");
                    assertThat(alert.count()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("柱图：目标 / 赢单 / 管道三项同单位，序列长度与横轴一致")
    void shouldChartTargetAgainstWon() {
        when(analyticsAppService.dashboard(any()))
                .thenReturn(dashboard(new BigDecimal("400000.00"), new BigDecimal("120000.00"),
                        new BigDecimal("30.00"), 0L));

        OverviewChart chart = provider.build(context("crm_sales")).charts().get(0);

        assertThat(chart.format()).isEqualTo(OverviewFormat.MONEY);
        assertThat(chart.categories()).containsExactly("目标", "已赢单", "加权管道");
        assertThat(chart.series()).singleElement()
                .satisfies(series -> assertThat(series.values())
                        .containsExactly(400000d, 120000d, 180000d));
    }

    @Test
    @DisplayName("入口：客户台账，权限码复用 2D 客户列表")
    void shouldLinkCustomerPage() {
        when(analyticsAppService.dashboard(any()))
                .thenReturn(dashboard(new BigDecimal("400000.00"), new BigDecimal("120000.00"),
                        new BigDecimal("30.00"), 0L));

        List<OverviewLink> links = provider.build(context("crm_sales")).links();

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.path()).isEqualTo("/crm/customer/page");
            assertThat(link.permission()).isEqualTo("sys:crm:customer:list");
        });
    }

    @Test
    @DisplayName("数据范围：角色原样交给 CrmActor，管理员看全租户、普通销售只看本人")
    void shouldForwardRolesToActor() {
        when(analyticsAppService.dashboard(any()))
                .thenReturn(dashboard(new BigDecimal("400000.00"), new BigDecimal("120000.00"),
                        new BigDecimal("30.00"), 0L));

        provider.build(context("crm_admin"));
        provider.build(context("crm_sales"));

        ArgumentCaptor<CrmActor> captor = ArgumentCaptor.forClass(CrmActor.class);
        verify(analyticsAppService, org.mockito.Mockito.times(2)).dashboard(captor.capture());

        CrmActor admin = captor.getAllValues().get(0);
        CrmActor sales = captor.getAllValues().get(1);
        assertThat(admin.allDataScope()).isTrue();
        assertThat(sales.allDataScope()).isFalse();
        assertThat(sales.getUserId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("门禁关闭：异常必须往上抛，由 SDK 降级成闭门，不能吞成一片 0")
    void shouldPropagateGateClosed() {
        when(analyticsAppService.dashboard(any()))
                .thenThrow(CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED));

        assertThatThrownBy(() -> provider.build(context("crm_sales")))
                .isInstanceOf(RuntimeException.class);
    }
}

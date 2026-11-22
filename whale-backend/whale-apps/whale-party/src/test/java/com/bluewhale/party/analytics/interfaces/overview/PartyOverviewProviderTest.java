package com.bluewhale.party.analytics.interfaces.overview;

import com.bluewhale.overview.model.OverviewAlert;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewChart;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewLink;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.party.analytics.application.service.AnalyticsAppService;
import com.bluewhale.party.analytics.application.view.MetricItemView;
import com.bluewhale.party.analytics.application.view.MetricOverviewView;
import com.bluewhale.party.analytics.domain.model.MetricType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PartyOverviewProvider} 单元测试：四张牌的口径提示、收缴率的语气分界、
 * 年龄结构柱图、告警分条。
 *
 * @author BlueWhale
 */
class PartyOverviewProviderTest {

    private final AnalyticsAppService analyticsAppService = mock(AnalyticsAppService.class);

    private final PartyOverviewProvider provider = new PartyOverviewProvider(analyticsAppService);

    /**
     * 构造一个指标视图。
     *
     * @param type        指标类型
     * @param numerator   分子
     * @param denominator 分母（可空）
     * @param rate        百分数比率（可空）
     * @return 指标视图
     */
    private static MetricItemView item(MetricType type, String numerator,
            String denominator, String rate) {
        MetricItemView view = new MetricItemView();
        view.setMetricType(type.name());
        view.setLabel(type.getLabel());
        view.setNumerator(numerator == null ? null : new BigDecimal(numerator));
        view.setDenominator(denominator == null ? null : new BigDecimal(denominator));
        view.setRate(rate == null ? null : new BigDecimal(rate));
        return view;
    }

    /**
     * 构造一份党务看板总览。
     *
     * @param items 指标列表
     * @param live  是否实时算（当日无快照）
     * @return 总览
     */
    private static MetricOverviewView view(List<MetricItemView> items, boolean live) {
        MetricOverviewView overview = new MetricOverviewView();
        overview.setItems(new ArrayList<>(items));
        overview.setLive(live);
        return overview;
    }

    /**
     * 一份健康的党务账本：42 名党员、收缴 96.5%、开展 92%、参会 88%。
     *
     * @return 指标列表
     */
    private static List<MetricItemView> healthy() {
        return List.of(
                item(MetricType.MEMBER_TOTAL, "42", null, null),
                item(MetricType.PROBATION_DUE, "3", null, null),
                item(MetricType.FEE_COLLECTION_AMOUNT, "96500", "100000", "96.50"),
                item(MetricType.ACTIVITY_HOLD, "23", "25", "92.00"),
                item(MetricType.ACTIVITY_ATTENDANCE, "440", "500", "88.00"),
                item(MetricType.MEMBER_AGE_UNDER_30, "8", null, null),
                item(MetricType.MEMBER_AGE_30_39, "12", null, null),
                item(MetricType.MEMBER_AGE_40_49, "10", null, null),
                item(MetricType.MEMBER_AGE_50_59, "7", null, null),
                item(MetricType.MEMBER_AGE_60_PLUS, "4", null, null),
                item(MetricType.MEMBER_AGE_UNKNOWN, "1", null, null));
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
    @DisplayName("元信息与权限码：复用 2D 党务看板同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("party");
        assertThat(provider.nodeKey()).isEqualTo("xuanfu-court");
        assertThat(provider.title()).isEqualTo("宣抚");
        assertThat(provider.subtitle()).isEqualTo("党务党建");
        assertThat(provider.permissionCode()).isEqualTo("sys:party:metric:list");
    }

    @Test
    @DisplayName("四张牌：比率按 /100 进契约，提示语写出分子分母与待转正数")
    void shouldBuildFourKpisWithHints() {
        when(analyticsAppService.partyOverview(isNull(), isNull(), any()))
                .thenReturn(view(healthy(), false));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(null));

        assertThat(kpis.get("members").value()).isEqualTo(42d);
        assertThat(kpis.get("members").hint()).isEqualTo("待转正预备党员 3 人");
        assertThat(kpis.get("fee-rate").value()).isEqualTo(0.965);
        assertThat(kpis.get("fee-rate").hint()).isEqualTo("实收 96500/100000");
        assertThat(kpis.get("activity-hold").value()).isEqualTo(0.92);
        assertThat(kpis.get("activity-hold").hint()).isEqualTo("已开展 23/25");
        assertThat(kpis.get("attendance").value()).isEqualTo(0.88);
        assertThat(kpis.get("attendance").hint()).isEqualTo("实到 440/500");
    }

    @Test
    @DisplayName("收缴率语气：96.5% 绿、88% 黄、72% 红——分界钉死，不随手给灯")
    void shouldToneFeeRateByThresholds() {
        List<MetricItemView> base = new ArrayList<>(healthy());
        base.set(2, item(MetricType.FEE_COLLECTION_AMOUNT, "96500", "100000", "96.50"));
        when(analyticsAppService.partyOverview(isNull(), isNull(), any()))
                .thenReturn(view(base, false));
        assertThat(kpiMap(provider.build(null)).get("fee-rate").tone()).isEqualTo(OverviewTone.GOOD);

        base.set(2, item(MetricType.FEE_COLLECTION_AMOUNT, "88000", "100000", "88.00"));
        when(analyticsAppService.partyOverview(isNull(), isNull(), any()))
                .thenReturn(view(base, false));
        assertThat(kpiMap(provider.build(null)).get("fee-rate").tone()).isEqualTo(OverviewTone.WARN);

        base.set(2, item(MetricType.FEE_COLLECTION_AMOUNT, "72000", "100000", "72.00"));
        when(analyticsAppService.partyOverview(isNull(), isNull(), any()))
                .thenReturn(view(base, false));
        assertThat(kpiMap(provider.build(null)).get("fee-rate").tone()).isEqualTo(OverviewTone.BAD);
    }

    @Test
    @DisplayName("年龄结构柱图：六段分桶按固定顺序，格式是计数")
    void shouldChartAgeStructure() {
        when(analyticsAppService.partyOverview(isNull(), isNull(), any()))
                .thenReturn(view(healthy(), false));

        OverviewChart chart = provider.build(null).charts().get(0);

        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("30 岁以下党员", "30–39 岁党员",
                "40–49 岁党员", "50–59 岁党员", "60 岁及以上党员", "年龄未知党员");
        assertThat(chart.series()).hasSize(1);
        assertThat(chart.series().get(0).values()).containsExactly(8d, 12d, 10d, 7d, 4d, 1d);
    }

    @Test
    @DisplayName("入口：党员台账，权限码复用 2D 党员列表")
    void shouldLinkMemberPage() {
        when(analyticsAppService.partyOverview(isNull(), isNull(), any()))
                .thenReturn(view(healthy(), false));

        List<OverviewLink> links = provider.build(null).links();

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.path()).isEqualTo("/party/member/page");
            assertThat(link.permission()).isEqualTo("sys:party:member:list");
        });
    }

    @Test
    @DisplayName("告警：低收缴/低开展/低参会/换届到期是黄灯，待转正与实时算是说明")
    void shouldSeparateWarningsFromInfo() {
        List<MetricItemView> items = new ArrayList<>(healthy());
        items.set(2, item(MetricType.FEE_COLLECTION_AMOUNT, "72000", "100000", "72.00"));
        items.set(3, item(MetricType.ACTIVITY_HOLD, "16", "25", "64.00"));
        items.add(item(MetricType.BRANCH_TERM_DUE, "2", "9", "22.22"));
        when(analyticsAppService.partyOverview(isNull(), isNull(), any()))
                .thenReturn(view(items, true));

        OverviewSnapshot snapshot = provider.build(null);

        List<String> warnings = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .map(OverviewAlert::text)
                .toList();
        List<String> infos = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.INFO)
                .map(OverviewAlert::text)
                .toList();
        assertThat(warnings).anyMatch(text -> text.contains("党费收缴率仅 72%"))
                .anyMatch(text -> text.contains("三会一课开展率仅 64%"))
                .anyMatch(text -> text.contains("2 个支部到了换届期"));
        assertThat(infos).anyMatch(text -> text.contains("3 名预备党员到了转正期"))
                .anyMatch(text -> text.contains("今日还没有党务快照"));
    }

    @Test
    @DisplayName("没有任何指标：只提示无账可理，不误报低收缴与换届")
    void shouldNotBlameEmptyLedgers() {
        when(analyticsAppService.partyOverview(isNull(), isNull(), any()))
                .thenReturn(view(List.of(), false));

        OverviewSnapshot snapshot = provider.build(null);

        assertThat(kpiMap(snapshot).get("members").hint()).isEqualTo("党务看板还没有数据");
        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING
                        || alert.level() == OverviewAlertLevel.CRITICAL)
                .isEmpty();
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("无账可理"));
    }
}

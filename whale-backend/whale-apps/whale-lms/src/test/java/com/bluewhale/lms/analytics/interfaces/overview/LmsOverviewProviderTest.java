package com.bluewhale.lms.analytics.interfaces.overview;

import com.bluewhale.lms.analytics.application.service.AnalyticsAppService;
import com.bluewhale.lms.analytics.application.view.AnalyticsOverviewView;
import com.bluewhale.lms.analytics.application.view.MetricSnapshotView;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link LmsOverviewProvider} 单元测试：口径与 2D 学习看板同源、百分数换算成比率、
 * 趋势只取尾部窗口。
 *
 * @author BlueWhale
 */
class LmsOverviewProviderTest {

    /** 统计日 */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 21);

    private final AnalyticsAppService analyticsAppService = mock(AnalyticsAppService.class);

    private final LmsOverviewProvider provider = new LmsOverviewProvider(analyticsAppService);

    /**
     * 构造一份学习看板
     *
     * @param memberTotal       在职人数
     * @param learnerTotal      学习人数
     * @param learnerCompleted  完成人数
     * @param examTakerTotal    参考人数
     * @param examPassedTotal   通过人数
     * @return 看板总览
     */
    private static AnalyticsOverviewView overview(long memberTotal, long learnerTotal, long learnerCompleted,
            long examTakerTotal, long examPassedTotal) {
        AnalyticsOverviewView view = new AnalyticsOverviewView();
        view.setStatDate(DAY);
        view.setScopeType("ALL");
        view.setScopeName("全院");
        view.setMemberTotal(memberTotal);
        view.setLearnerTotal(learnerTotal);
        view.setCoverageRate(rate(learnerTotal, memberTotal));
        view.setLearnerCompleted(learnerCompleted);
        view.setCompletionRate(rate(learnerCompleted, learnerTotal));
        view.setExamTakerTotal(examTakerTotal);
        view.setExamPassedTotal(examPassedTotal);
        view.setPassRate(rate(examPassedTotal, examTakerTotal));
        view.setCreditTotal(new BigDecimal("120.00"));
        view.setCreditEarners(18L);
        return view;
    }

    /**
     * 与 {@code MetricSnapshot#rate} 同口径：百分数，保留两位
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 百分数
     */
    private static BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 构造趋势点
     *
     * @param day       统计日
     * @param learners  学习人数
     * @param completed 完成人数
     * @return 快照视图
     */
    private static MetricSnapshotView point(int day, long learners, long completed) {
        MetricSnapshotView view = new MetricSnapshotView();
        view.setStatDate(LocalDate.of(2026, 11, day));
        view.setLearnerTotal(learners);
        view.setLearnerCompleted(completed);
        return view;
    }

    /**
     * 具备学习看板与课程列表权限的调用者
     *
     * @return 上下文
     */
    private static OverviewContext context() {
        return OverviewContext.of(1001L, 1L,
                Set.of("sys:lms:analytics:view", "sys:lms:course:list"), Set.of("lms_admin"));
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
    @DisplayName("元信息与权限码：复用 2D 学习看板同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("lms");
        assertThat(provider.nodeKey()).isEqualTo("xingtan-hall");
        assertThat(provider.title()).isEqualTo("杏坛");
        assertThat(provider.subtitle()).isEqualTo("学习教化");
        assertThat(provider.permissionCode()).isEqualTo("sys:lms:analytics:view");
    }

    @Test
    @DisplayName("比率：2D 的百分数（63）必须换算成契约比率（0.63），否则屏风显示 6300%")
    void shouldConvertPercentToRatio() {
        when(analyticsAppService.overview(isNull(), any()))
                .thenReturn(overview(100L, 63L, 40L, 20L, 15L));

        OverviewKpi coverage = kpiMap(provider.build(context())).get("coverage");

        assertThat(coverage.format()).isEqualTo(OverviewFormat.PERCENT);
        assertThat(coverage.value()).isEqualTo(0.63d);
        /* 63% 过了及格线、未到健康线：黄灯 */
        assertThat(coverage.tone()).isEqualTo(OverviewTone.WARN);
    }

    @Test
    @DisplayName("两张比率牌的分母不同：覆盖率对在职人数，完成率对已学人数")
    void shouldWriteDifferentDenominators() {
        when(analyticsAppService.overview(isNull(), any()))
                .thenReturn(overview(100L, 40L, 30L, 20L, 15L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(context()));

        assertThat(kpis.get("learners").value()).isEqualTo(100d);
        assertThat(kpis.get("coverage").hint()).isEqualTo("已学 40/100");
        assertThat(kpis.get("completion").hint()).isEqualTo("学完 30/40");
        assertThat(kpis.get("completion").value()).isEqualTo(0.75d);
        /* 75% ≥ 50% 但 < 80%：黄灯 */
        assertThat(kpis.get("completion").tone()).isEqualTo(OverviewTone.WARN);
    }

    @Test
    @DisplayName("考试通过率：分母是参考人数，无人参考时给灰灯而不是红灯")
    void shouldHandleZeroExamTakers() {
        when(analyticsAppService.overview(isNull(), any()))
                .thenReturn(overview(100L, 63L, 40L, 0L, 0L));

        OverviewKpi passRate = kpiMap(provider.build(context())).get("pass-rate");

        assertThat(passRate.value()).isEqualTo(0d);
        assertThat(passRate.tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(passRate.hint()).isEqualTo("本月无人参考");
    }

    @Test
    @DisplayName("趋势：两条序列同量纲，长度与横轴一致")
    void shouldChartLearnersAndCompleted() {
        AnalyticsOverviewView view = overview(100L, 63L, 40L, 20L, 15L);
        view.setTrend(List.of(point(19, 50, 30), point(20, 58, 36), point(21, 63, 40)));
        when(analyticsAppService.overview(isNull(), any())).thenReturn(view);

        OverviewChart chart = provider.build(context()).charts().get(0);

        assertThat(chart.type()).isEqualTo("line");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("11-19", "11-20", "11-21");
        assertThat(chart.series()).hasSize(2);
        assertThat(chart.series().get(0).values()).containsExactly(50d, 58d, 63d);
        assertThat(chart.series().get(1).values()).containsExactly(30d, 36d, 40d);
    }

    @Test
    @DisplayName("趋势窗口：超过 14 个统计日只取尾部，30 个点在屏风上会糊成一条粗线")
    void shouldTrimTrendToTailWindow() {
        AnalyticsOverviewView view = overview(100L, 63L, 40L, 20L, 15L);
        List<MetricSnapshotView> trend = new ArrayList<>();
        for (int day = 1; day <= 21; day++) {
            trend.add(point(day, 10L + day, 5L + day));
        }
        view.setTrend(trend);
        when(analyticsAppService.overview(isNull(), any())).thenReturn(view);

        OverviewChart chart = provider.build(context()).charts().get(0);

        assertThat(chart.categories()).hasSize(14);
        assertThat(chart.categories().get(0)).isEqualTo("11-08");
        assertThat(chart.categories().get(13)).isEqualTo("11-21");
    }

    @Test
    @DisplayName("入口：课程目录，权限码复用 2D 课程列表")
    void shouldLinkCoursePage() {
        when(analyticsAppService.overview(isNull(), any()))
                .thenReturn(overview(100L, 63L, 40L, 20L, 15L));

        List<OverviewLink> links = provider.build(context()).links();

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.path()).isEqualTo("/lms/v1/course/page");
            assertThat(link.permission()).isEqualTo("sys:lms:course:list");
        });
    }

    @Test
    @DisplayName("告警：覆盖低、完成低、通过率低各一条，且都带百分数")
    void shouldWarnOnLowRates() {
        when(analyticsAppService.overview(isNull(), any()))
                .thenReturn(overview(100L, 30L, 10L, 20L, 5L));

        List<OverviewAlert> warnings = provider.build(context()).alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .toList();

        assertThat(warnings).hasSize(3);
        assertThat(warnings).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("学习覆盖率仅 30%"))
                .anyMatch(text -> text.contains("只有 33% 学完"))
                .anyMatch(text -> text.contains("考试通过率 25%"));
    }

    @Test
    @DisplayName("告警：今日无快照与趋势为空是两件事，各说一句")
    void shouldExplainMissingSnapshot() {
        AnalyticsOverviewView view = overview(100L, 63L, 40L, 20L, 15L);
        view.setLive(true);
        when(analyticsAppService.overview(isNull(), any())).thenReturn(view);

        List<String> texts = provider.build(context()).alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.INFO)
                .map(OverviewAlert::text)
                .toList();

        assertThat(texts).anyMatch(text -> text.contains("实时算的"))
                .anyMatch(text -> text.contains("还没有历史快照"));
    }

    @Test
    @DisplayName("没有员工档案：不报覆盖率红灯，只提示无生可教")
    void shouldNotBlameZeroMembers() {
        when(analyticsAppService.overview(isNull(), any()))
                .thenReturn(overview(0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(kpiMap(snapshot).get("coverage").tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(kpiMap(snapshot).get("coverage").hint()).isEqualTo("尚无在职人数");
        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .isEmpty();
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("无生可教"));
    }

    @Test
    @DisplayName("门禁关闭：异常必须往上抛，由 SDK 降级成闭门，不能吞成一片 0")
    void shouldPropagateGateClosed() {
        when(analyticsAppService.overview(isNull(), any()))
                .thenThrow(LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED));

        assertThatThrownBy(() -> provider.build(context()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("上下文为空：退化为匿名操作人，不抛空指针")
    void shouldTolerateNullContext() {
        when(analyticsAppService.overview(isNull(), any(LmsActor.class)))
                .thenReturn(overview(100L, 63L, 40L, 20L, 15L));

        assertThat(provider.build(null).kpis()).hasSize(4);
    }
}

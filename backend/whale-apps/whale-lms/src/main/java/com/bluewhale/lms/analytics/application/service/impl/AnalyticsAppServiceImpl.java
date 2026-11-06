package com.bluewhale.lms.analytics.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.analytics.application.service.AnalyticsAppService;
import com.bluewhale.lms.analytics.application.view.AnalyticsOverviewView;
import com.bluewhale.lms.analytics.application.view.MetricSnapshotView;
import com.bluewhale.lms.analytics.application.view.RefreshResultView;
import com.bluewhale.lms.analytics.domain.model.MetricQuery;
import com.bluewhale.lms.analytics.domain.model.MetricScope;
import com.bluewhale.lms.analytics.domain.model.MetricSnapshot;
import com.bluewhale.lms.analytics.domain.repository.MetricSnapshotRepository;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.course.application.service.LearningAppService;
import com.bluewhale.lms.course.application.view.LearningMetricsView;
import com.bluewhale.lms.credit.application.service.CreditAppService;
import com.bluewhale.lms.credit.application.view.CreditMetricsView;
import com.bluewhale.lms.exam.application.service.AttemptAppService;
import com.bluewhale.lms.exam.application.view.ExamMetricsView;
import com.bluewhale.lms.integration.application.service.MemberSnapshotAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * 学习看板应用服务实现。
 *
 * <p>三条口径决定：</p>
 * <ol>
 *   <li><b>取数走各上下文的 {@code metrics()}，不直接读别人的表</b>：
 *       看板是只读投影，一旦直接写 SQL 查课程表、考试表，任何一方改表结构都会在
 *       看板上炸出编译期发现不了的错误；</li>
 *   <li><b>读接口不写库</b>：总览在缺快照时实时算但不落库，否则每访问一次就多一份快照，
 *       「每日一快照」的历史曲线会被访问频率污染；</li>
 *   <li><b>导出只有聚合数</b>：看板不存人名，导出自然不含个人信息，
 *       所谓脱敏在这里不是打码，而是「从一开始就不把明细带出来」。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AnalyticsAppServiceImpl implements AnalyticsAppService {

    /** 趋势默认取最近 30 个统计日 */
    private static final int TREND_DAYS = 30;

    /** 导出天数上限（一年足够，再多请走历史快照分页） */
    private static final int EXPORT_MAX_DAYS = 366;

    /** CSV 表头 */
    private static final String CSV_HEADER =
            "统计日,统计范围,在职人数,学习人数,学习覆盖率(%),完成人数,学习完成率(%),"
                    + "参考人数,通过人数,通过率(%),学分总量,获学分人数";

    private final MetricSnapshotRepository metricSnapshotRepository;

    private final LearningAppService learningAppService;

    private final AttemptAppService attemptAppService;

    private final CreditAppService creditAppService;

    private final MemberSnapshotAppService memberSnapshotAppService;

    private final LmsProperties properties;

    @Override
    public AnalyticsOverviewView overview(LocalDate statDate, LmsActor actor) {
        assertGate();
        LocalDate day = statDate == null ? LocalDate.now() : statDate;
        Optional<MetricSnapshot> snapshot = metricSnapshotRepository.find(day, MetricScope.ALL, 0L);
        MetricSnapshot source = snapshot.orElseGet(() -> compute(day));
        CreditMetricsView credit = creditAppService.metrics();
        AnalyticsOverviewView view = toOverview(source, credit);
        view.setLive(snapshot.isEmpty());
        List<MetricSnapshotView> trend = metricSnapshotRepository.listRecentAll(TREND_DAYS)
                .stream().map(MetricSnapshotView::from).toList();
        view.setTrend(trend);
        return view;
    }

    @Override
    @Transactional
    public RefreshResultView refresh(LocalDate statDate, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        LocalDate day = statDate == null ? LocalDate.now() : statDate;
        boolean existed = metricSnapshotRepository.find(day, MetricScope.ALL, 0L).isPresent();
        MetricSnapshot computed = compute(day);
        metricSnapshotRepository.save(computed);
        RefreshResultView view = new RefreshResultView();
        view.setStatDate(day.toString());
        view.setCreated(!existed);
        view.setSnapshot(MetricSnapshotView.from(computed));
        return view;
    }

    @Override
    public PageResult<MetricSnapshotView> page(MetricQuery query, LmsActor actor) {
        assertGate();
        long current = Math.max(query.getCurrent() == null ? 1L : query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize() == null ? 20L : query.getSize(), 1L),
                properties.getMetricPageLimit());
        long total = metricSnapshotRepository.count(query);
        List<MetricSnapshotView> views = metricSnapshotRepository.page(query, (current - 1) * size, (int) size)
                .stream().map(MetricSnapshotView::from).toList();
        return PageResult.of(views, total, current, size);
    }

    @Override
    public String exportCsv(int days, LmsActor actor) {
        assertGate();
        if (days <= 0) {
            throw LmsErrorCode.exception(LmsErrorCode.METRIC_INVALID, "导出天数必须大于 0");
        }
        int limit = Math.min(days, EXPORT_MAX_DAYS);
        List<MetricSnapshot> snapshots = metricSnapshotRepository.listRecentAll(limit);
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(CSV_HEADER);
        for (MetricSnapshot snapshot : snapshots) {
            joiner.add(String.join(",",
                    snapshot.getStatDate().toString(),
                    snapshot.getScopeName(),
                    String.valueOf(snapshot.getMemberTotal()),
                    String.valueOf(snapshot.getLearnerTotal()),
                    percent(snapshot.coverageRate()),
                    String.valueOf(snapshot.getLearnerCompleted()),
                    percent(snapshot.completionRate()),
                    String.valueOf(snapshot.getExamTakerTotal()),
                    String.valueOf(snapshot.getExamPassedTotal()),
                    percent(snapshot.passRate()),
                    plain(snapshot.getCreditTotal()),
                    String.valueOf(snapshot.getCreditEarners())));
        }
        return joiner.toString();
    }

    /**
     * 实时计算某统计日的全院快照（不落库）。
     *
     * @param day 统计日
     * @return 已校验的快照
     */
    private MetricSnapshot compute(LocalDate day) {
        LearningMetricsView learning = learningAppService.metrics();
        ExamMetricsView exam = attemptAppService.metrics();
        CreditMetricsView credit = creditAppService.metrics();
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setScopeType(MetricScope.ALL);
        snapshot.setScopeId(0L);
        snapshot.setScopeName(MetricScope.ALL.getLabel());
        snapshot.setMemberTotal(memberSnapshotAppService.countActive());
        snapshot.setLearnerTotal(learning.getLearnerTotal());
        snapshot.setLearnerCompleted(learning.getLearnerCompleted());
        snapshot.setExamTakerTotal(exam.getTakerTotal());
        snapshot.setExamPassedTotal(exam.getPassedTotal());
        snapshot.setCreditTotal(credit.getTotalCredit());
        snapshot.setCreditEarners(credit.getEarners());
        return snapshot.validate(day);
    }

    /**
     * 快照转总览视图。
     *
     * @param snapshot 快照
     * @param credit 学分指标（分布实时算，不落库）
     * @return 总览
     */
    private AnalyticsOverviewView toOverview(MetricSnapshot snapshot, CreditMetricsView credit) {
        AnalyticsOverviewView view = new AnalyticsOverviewView();
        view.setStatDate(snapshot.getStatDate());
        view.setScopeType(snapshot.getScopeType().name());
        view.setScopeName(snapshot.getScopeName());
        view.setMemberTotal(snapshot.getMemberTotal());
        view.setLearnerTotal(snapshot.getLearnerTotal());
        view.setCoverageRate(snapshot.coverageRate());
        view.setLearnerCompleted(snapshot.getLearnerCompleted());
        view.setCompletionRate(snapshot.completionRate());
        view.setExamTakerTotal(snapshot.getExamTakerTotal());
        view.setExamPassedTotal(snapshot.getExamPassedTotal());
        view.setPassRate(snapshot.passRate());
        view.setCreditTotal(snapshot.getCreditTotal());
        view.setCreditEarners(snapshot.getCreditEarners());
        view.setCreditDistribution(credit.getDistribution());
        return view;
    }

    /**
     * 百分比转 CSV 文本。
     *
     * @param value 百分比
     * @return 文本
     */
    private String percent(BigDecimal value) {
        return value == null ? "0.0" : value.toPlainString();
    }

    /**
     * 数值转 CSV 文本。
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }
}

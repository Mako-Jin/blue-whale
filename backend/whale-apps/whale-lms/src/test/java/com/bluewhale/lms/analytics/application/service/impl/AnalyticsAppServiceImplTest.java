package com.bluewhale.lms.analytics.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
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
import com.bluewhale.lms.credit.domain.model.CreditBucket;
import com.bluewhale.lms.exam.application.service.AttemptAppService;
import com.bluewhale.lms.exam.application.view.ExamMetricsView;
import com.bluewhale.lms.integration.application.service.MemberSnapshotAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 看板服务：读接口不落库、刷新幂等覆盖、分页上限、导出聚合口径。
 *
 * @author BlueWhale
 */
class AnalyticsAppServiceImplTest {

    /** 统计日 */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 6);

    /** 管理员操作人 */
    private final LmsActor admin = new LmsActor(1L, "admin", Set.of("lms_admin"), Set.of());

    private MetricSnapshotRepository metricSnapshotRepository;

    private LearningAppService learningAppService;

    private AttemptAppService attemptAppService;

    private CreditAppService creditAppService;

    private MemberSnapshotAppService memberSnapshotAppService;

    private LmsProperties properties;

    private AnalyticsAppServiceImpl service;

    @BeforeEach
    void setUp() {
        metricSnapshotRepository = mock(MetricSnapshotRepository.class);
        learningAppService = mock(LearningAppService.class);
        attemptAppService = mock(AttemptAppService.class);
        creditAppService = mock(CreditAppService.class);
        memberSnapshotAppService = mock(MemberSnapshotAppService.class);
        properties = new LmsProperties();
        service = new AnalyticsAppServiceImpl(metricSnapshotRepository, learningAppService, attemptAppService,
                creditAppService, memberSnapshotAppService, properties);
        stubMetrics();
        when(metricSnapshotRepository.listRecentAll(anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("当天没有快照时实时计算并标记 live，且不写库")
    void overviewShouldComputeLiveWithoutSaving() {
        when(metricSnapshotRepository.find(DAY, MetricScope.ALL, 0L)).thenReturn(Optional.empty());
        AnalyticsOverviewView view = service.overview(DAY, admin);
        assertTrue(view.isLive());
        assertEquals(12, view.getMemberTotal());
        assertEquals(6, view.getLearnerTotal());
        assertEquals(3, view.getLearnerCompleted());
        assertEquals(new BigDecimal("50.0"), view.getCoverageRate());
        assertEquals(3, view.getExamTakerTotal());
        assertEquals(1, view.getExamPassedTotal());
        assertEquals(new BigDecimal("33.3"), view.getPassRate());
        assertEquals(4, view.getCreditDistribution().size());
        verify(metricSnapshotRepository, never()).save(any(MetricSnapshot.class));
    }

    @Test
    @DisplayName("当天已有快照时直接读快照，标记非实时")
    void overviewShouldUseSnapshotWhenPresent() {
        when(metricSnapshotRepository.find(DAY, MetricScope.ALL, 0L)).thenReturn(Optional.of(snapshot()));
        AnalyticsOverviewView view = service.overview(DAY, admin);
        assertFalse(view.isLive());
        assertEquals(10, view.getMemberTotal());
        assertEquals(5, view.getLearnerTotal());
        assertEquals(new BigDecimal("50.0"), view.getCoverageRate());
        verify(learningAppService, never()).metrics();
    }

    @Test
    @DisplayName("刷新时首次新建，已存在则覆盖")
    void refreshShouldReportCreatedFlag() {
        when(metricSnapshotRepository.find(DAY, MetricScope.ALL, 0L)).thenReturn(Optional.empty());
        RefreshResultView created = service.refresh(DAY, admin);
        assertTrue(created.isCreated());
        assertEquals("2026-11-06", created.getStatDate());
        verify(metricSnapshotRepository).save(any(MetricSnapshot.class));

        when(metricSnapshotRepository.find(DAY, MetricScope.ALL, 0L)).thenReturn(Optional.of(snapshot()));
        RefreshResultView updated = service.refresh(DAY, admin);
        assertFalse(updated.isCreated());
    }

    @Test
    @DisplayName("分页条数被配置上限截断")
    void pageShouldClampSize() {
        properties.setMetricPageLimit(5);
        MetricQuery query = new MetricQuery();
        query.setCurrent(1L);
        query.setSize(50L);
        when(metricSnapshotRepository.count(query)).thenReturn(0L);
        when(metricSnapshotRepository.page(query, 0L, 5)).thenReturn(List.of());
        PageResult<MetricSnapshotView> result = service.page(query, admin);
        assertEquals(0, result.getTotal());
        verify(metricSnapshotRepository).page(eq(query), eq(0L), eq(5));
    }

    @Test
    @DisplayName("导出输出表头与每行快照，且不含个人信息列")
    void exportShouldRenderAggregatedCsv() {
        when(metricSnapshotRepository.listRecentAll(30)).thenReturn(List.of(snapshot()));
        String csv = service.exportCsv(30, admin);
        String[] lines = csv.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].startsWith("统计日,统计范围,在职人数"));
        assertTrue(lines[1].startsWith("2026-11-06,全院,10,5,50.0"));
        assertFalse(csv.contains("username"));
    }

    @Test
    @DisplayName("导出天数非法时拒绝")
    void exportShouldRejectNonPositiveDays() {
        BizException exception = assertThrows(BizException.class, () -> service.exportCsv(0, admin));
        assertEquals(LmsErrorCode.METRIC_INVALID, exception.getCode());
    }

    /**
     * 打桩各域指标：在职 12、学习 6（完成 3）、参考 3（通过 1）、学分 20.0（4 人）。
     */
    private void stubMetrics() {
        LearningMetricsView learning = new LearningMetricsView();
        learning.setEnrollTotal(9);
        learning.setLearnerTotal(6);
        learning.setLearnerCompleted(3);
        when(learningAppService.metrics()).thenReturn(learning);

        ExamMetricsView exam = new ExamMetricsView();
        exam.setTakerTotal(3);
        exam.setPassedTotal(1);
        when(attemptAppService.metrics()).thenReturn(exam);

        CreditMetricsView credit = new CreditMetricsView();
        credit.setTotalCredit(new BigDecimal("20.0"));
        credit.setEarners(4);
        credit.setDistribution(List.of(bucket("0-5", 2), bucket("5-10", 1),
                bucket("10-20", 1), bucket("20+", 0)));
        when(creditAppService.metrics()).thenReturn(credit);

        when(memberSnapshotAppService.countActive()).thenReturn(12L);
    }

    /**
     * 构造学分分布档位。
     *
     * @param label 档位
     * @param earners 人数
     * @return 档位
     */
    private CreditBucket bucket(String label, long earners) {
        CreditBucket bucket = new CreditBucket();
        bucket.setLabel(label);
        bucket.setEarners(earners);
        return bucket;
    }

    /**
     * 构造一份既有快照：在职 10、学习 5（完成 2）、参考 2（通过 1）。
     *
     * @return 快照
     */
    private MetricSnapshot snapshot() {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setStatDate(DAY);
        snapshot.setScopeType(MetricScope.ALL);
        snapshot.setScopeId(0L);
        snapshot.setScopeName("全院");
        snapshot.setMemberTotal(10);
        snapshot.setLearnerTotal(5);
        snapshot.setLearnerCompleted(2);
        snapshot.setExamTakerTotal(2);
        snapshot.setExamPassedTotal(1);
        snapshot.setCreditTotal(new BigDecimal("8.0"));
        snapshot.setCreditEarners(2);
        snapshot.validate(DAY);
        return snapshot;
    }
}

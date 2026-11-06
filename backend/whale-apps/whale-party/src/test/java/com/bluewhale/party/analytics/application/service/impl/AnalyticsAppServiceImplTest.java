package com.bluewhale.party.analytics.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.activity.application.service.ActivityAppService;
import com.bluewhale.party.activity.application.view.ActivityMetricView;
import com.bluewhale.party.analytics.application.view.MetricItemView;
import com.bluewhale.party.analytics.application.view.MetricOverviewView;
import com.bluewhale.party.analytics.domain.model.MetricScope;
import com.bluewhale.party.analytics.domain.model.MetricSnapshot;
import com.bluewhale.party.analytics.domain.model.MetricType;
import com.bluewhale.party.analytics.domain.repository.MetricSnapshotRepository;
import com.bluewhale.party.branch.application.service.BranchAppService;
import com.bluewhale.party.branch.application.view.BranchScopeView;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.fee.application.service.FeeAppService;
import com.bluewhale.party.fee.application.view.FeeMetricView;
import com.bluewhale.party.integration.application.service.EmployeeSnapshotAppService;
import com.bluewhale.party.member.application.service.MemberAppService;
import com.bluewhale.party.member.application.view.MemberMetricView;
import com.bluewhale.party.union.application.service.UnionActivityAppService;
import com.bluewhale.party.union.application.service.UnionMemberAppService;
import com.bluewhale.party.union.application.service.UnionProposalAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 看板与统计应用服务：实时兜底、范围收敛、导出脱敏、区间上限。
 *
 * @author BlueWhale
 */
class AnalyticsAppServiceImplTest {

    private MetricSnapshotRepository metricSnapshotRepository;

    private MemberAppService memberAppService;

    private FeeAppService feeAppService;

    private ActivityAppService activityAppService;

    private BranchAppService branchAppService;

    private AnalyticsAppServiceImpl service;

    private PartyActor admin;

    @BeforeEach
    void setUp() {
        metricSnapshotRepository = mock(MetricSnapshotRepository.class);
        memberAppService = mock(MemberAppService.class);
        feeAppService = mock(FeeAppService.class);
        activityAppService = mock(ActivityAppService.class);
        branchAppService = mock(BranchAppService.class);
        service = new AnalyticsAppServiceImpl(metricSnapshotRepository, memberAppService,
                feeAppService, activityAppService, branchAppService,
                mock(UnionMemberAppService.class), mock(UnionActivityAppService.class),
                mock(UnionProposalAppService.class), mock(EmployeeSnapshotAppService.class),
                new PartyProperties());
        admin = new PartyActor(9L, "padmin", Set.of("party_admin"), Set.of());
    }

    @Test
    @DisplayName("无快照时实时计算，收缴率按分子分母现算")
    void overviewShouldFallbackToLiveMetrics() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        when(metricSnapshotRepository.listByDate(date, MetricScope.ALL, MetricSnapshot.ALL_SCOPE_ID))
                .thenReturn(List.of());
        when(metricSnapshotRepository.listTrend(any(), any(), eq(MetricType.FEE_COLLECTION_AMOUNT.name()),
                eq(MetricScope.ALL), eq(MetricSnapshot.ALL_SCOPE_ID))).thenReturn(List.of());
        when(memberAppService.metrics(date, null)).thenReturn(memberMetric());
        when(feeAppService.metrics("202611", null)).thenReturn(feeMetric());
        when(activityAppService.metrics(date, null)).thenReturn(activityMetric());
        when(branchAppService.termDue(null, null)).thenReturn(List.of());
        when(branchAppService.activeScopes()).thenReturn(List.of());

        MetricOverviewView view = service.partyOverview(date, null, admin);

        assertTrue(view.isLive());
        assertEquals("ALL", view.getScopeType());
        assertEquals(3L, item(view, MetricType.MEMBER_TOTAL).getNumerator().longValue());
        assertEquals(0, new BigDecimal("50.00").compareTo(item(view, MetricType.FEE_COLLECTION_AMOUNT).getRate()));
        assertEquals(0, new BigDecimal("50.00").compareTo(item(view, MetricType.ACTIVITY_ATTENDANCE).getRate()));
    }

    @Test
    @DisplayName("有快照时直接读快照，不再实时计算")
    void overviewShouldUseStoredSnapshots() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        when(metricSnapshotRepository.listByDate(date, MetricScope.ALL, MetricSnapshot.ALL_SCOPE_ID))
                .thenReturn(List.of(snapshot(MetricScope.ALL, MetricSnapshot.ALL_SCOPE_ID, "全租户",
                        MetricType.MEMBER_TOTAL, 8, 0)));
        when(metricSnapshotRepository.listTrend(any(), any(), any(), any(), any())).thenReturn(List.of());

        MetricOverviewView view = service.partyOverview(date, null, admin);

        assertFalse(view.isLive());
        assertEquals(8L, item(view, MetricType.MEMBER_TOTAL).getNumerator().longValue());
        verify(memberAppService, never()).metrics(any(), any());
    }

    @Test
    @DisplayName("支部书记看板强制本支部范围")
    void secretaryShouldBeForcedToOwnBranch() {
        PartyActor secretary = new PartyActor(9L, "secretary", Set.of("branch_secretary"), Set.of());
        LocalDate date = LocalDate.of(2026, 11, 6);
        when(memberAppService.branchIdOf(9L)).thenReturn(7L);
        when(branchAppService.activeScopes())
                .thenReturn(List.of(scope(7L, "第七党支部")));
        when(metricSnapshotRepository.listByDate(date, MetricScope.BRANCH, 7L))
                .thenReturn(List.of(snapshot(MetricScope.BRANCH, 7L, "第七党支部",
                        MetricType.MEMBER_TOTAL, 5, 0)));
        when(metricSnapshotRepository.listTrend(any(), any(), any(), any(), any())).thenReturn(List.of());

        MetricOverviewView view = service.partyOverview(date, null, secretary);

        assertEquals("BRANCH", view.getScopeType());
        assertEquals(7L, view.getScopeId().longValue());
    }

    @Test
    @DisplayName("无支部的支部书记被拒")
    void secretaryWithoutBranchShouldFail() {
        PartyActor secretary = new PartyActor(9L, "secretary", Set.of("branch_secretary"), Set.of());
        when(memberAppService.branchIdOf(9L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> service.partyOverview(LocalDate.of(2026, 11, 6), null, secretary));
        assertEquals(PartyErrorCode.BRANCH_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("刷新覆盖写全租户与每个支部")
    void refreshShouldCoverAllAndEveryBranch() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        when(memberAppService.metrics(date, null)).thenReturn(memberMetric());
        when(feeAppService.metrics("202611", null)).thenReturn(feeMetric());
        when(activityAppService.metrics(date, null)).thenReturn(activityMetric());
        when(branchAppService.termDue(null, null)).thenReturn(List.of());
        when(branchAppService.activeScopes()).thenReturn(List.of(scope(1L, "第一党支部")));
        when(memberAppService.metrics(date, 1L)).thenReturn(memberMetric());
        when(feeAppService.metrics("202611", 1L)).thenReturn(feeMetric());
        when(activityAppService.metrics(date, 1L)).thenReturn(activityMetric());

        service.refreshParty(date, admin);

        ArgumentCaptor<List<MetricSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricSnapshotRepository).saveAll(captor.capture());
        assertTrue(captor.getValue().stream().anyMatch(item -> "ALL".equals(item.getScopeType())));
        assertTrue(captor.getValue().stream()
                .anyMatch(item -> "BRANCH".equals(item.getScopeType()) && item.getScopeId() == 1L));
    }

    @Test
    @DisplayName("导出 CSV 只含聚合数，不含姓名与证件手机号")
    void exportShouldNotContainPersonalFields() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        when(metricSnapshotRepository.listBetween(date, date, MetricScope.ALL, MetricSnapshot.ALL_SCOPE_ID))
                .thenReturn(List.of(snapshot(MetricScope.ALL, MetricSnapshot.ALL_SCOPE_ID, "全租户",
                        MetricType.MEMBER_TOTAL, 3, 0)));

        String csv = service.exportPartyCsv(date, date, null, admin);

        assertTrue(csv.startsWith("\uFEFF统计日,统计范围,指标编码,指标名称,分子,分母,比率(%)"));
        assertTrue(csv.contains(MetricType.MEMBER_TOTAL.name()));
        assertFalse(csv.contains("张三"));
        assertFalse(csv.contains("13800138000"));
        assertFalse(csv.contains("身份证"));
        assertFalse(csv.contains("手机号"));
    }

    @Test
    @DisplayName("导出区间超过上限被拒")
    void exportShouldRejectOversizedRange() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2027, 6, 30);

        BizException exception = assertThrows(BizException.class,
                () -> service.exportPartyCsv(from, to, null, admin));
        assertEquals(PartyErrorCode.METRIC_EXPORT_LIMIT, exception.getCode());
        verify(metricSnapshotRepository, never()).listBetween(any(), any(), any(), anyLong());
    }

    /**
     * 构造党员结构统计。
     *
     * @return 统计
     */
    private MemberMetricView memberMetric() {
        MemberMetricView view = new MemberMetricView();
        view.setTotal(3L);
        view.setProbationDue(1L);
        return view;
    }

    /**
     * 构造党费收缴统计。
     *
     * @return 统计
     */
    private FeeMetricView feeMetric() {
        FeeMetricView view = new FeeMetricView();
        view.setDueAmount(new BigDecimal("200.00"));
        view.setPaidAmount(new BigDecimal("100.00"));
        view.setDueBillCount(2L);
        view.setPaidBillCount(1L);
        return view;
    }

    /**
     * 构造组织生活统计。
     *
     * @return 统计
     */
    private ActivityMetricView activityMetric() {
        ActivityMetricView view = new ActivityMetricView();
        view.setPlannedCount(2L);
        view.setHeldCount(1L);
        view.setSigninCount(10L);
        view.setPresentCount(5L);
        return view;
    }

    /**
     * 构造范围。
     *
     * @param branchId 支部 ID
     * @param branchName 支部名称
     * @return 范围
     */
    private BranchScopeView scope(Long branchId, String branchName) {
        BranchScopeView view = new BranchScopeView();
        view.setBranchId(branchId);
        view.setBranchName(branchName);
        return view;
    }

    /**
     * 构造快照。
     *
     * @param scope 范围
     * @param scopeId 范围 ID
     * @param scopeName 范围名称
     * @param type 指标
     * @param numerator 分子
     * @param denominator 分母
     * @return 快照
     */
    private MetricSnapshot snapshot(MetricScope scope, Long scopeId, String scopeName,
                                    MetricType type, long numerator, long denominator) {
        return MetricSnapshot.of(LocalDate.of(2026, 11, 6), type.name(), scope, scopeId, scopeName,
                BigDecimal.valueOf(numerator), BigDecimal.valueOf(denominator));
    }

    /**
     * 按指标编码取视图。
     *
     * @param view 看板
     * @param type 指标
     * @return 指标视图
     */
    private MetricItemView item(MetricOverviewView view, MetricType type) {
        return view.getItems().stream()
                .filter(item -> type.name().equals(item.getMetricType()))
                .findFirst().orElseThrow();
    }
}

package com.bluewhale.crm.sales.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.sales.application.command.ForecastAdjustCommand;
import com.bluewhale.crm.sales.application.command.ForecastGenerateCommand;
import com.bluewhale.crm.sales.application.view.ForecastSnapshotView;
import com.bluewhale.crm.sales.domain.model.ForecastAdjustment;
import com.bluewhale.crm.sales.domain.model.ForecastPeriodType;
import com.bluewhale.crm.sales.domain.model.ForecastScopeType;
import com.bluewhale.crm.sales.domain.model.ForecastSnapshot;
import com.bluewhale.crm.sales.domain.model.Opportunity;
import com.bluewhale.crm.sales.domain.model.OpportunityStage;
import com.bluewhale.crm.sales.domain.repository.ForecastAdjustmentRepository;
import com.bluewhale.crm.sales.domain.repository.ForecastSnapshotRepository;
import com.bluewhale.crm.sales.domain.repository.OpportunityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ForecastAppServiceImpl} 单元测试：预测口径、周期校验、重算幂等与调整留痕。
 *
 * @author BlueWhale
 */
class ForecastAppServiceImplTest {

    private ForecastSnapshotRepository snapshotRepository;

    private ForecastAdjustmentRepository adjustmentRepository;

    private OpportunityRepository opportunityRepository;

    private CrmProperties properties;

    private ForecastAppServiceImpl service;

    @BeforeEach
    void setUp() {
        snapshotRepository = mock(ForecastSnapshotRepository.class);
        adjustmentRepository = mock(ForecastAdjustmentRepository.class);
        opportunityRepository = mock(OpportunityRepository.class);
        properties = new CrmProperties();
        service = new ForecastAppServiceImpl(snapshotRepository, adjustmentRepository,
                opportunityRepository, properties);
    }

    @Test
    @DisplayName("门禁关闭：预测接口拒绝")
    void shouldRejectWhenGateClosed() {
        properties.setDemandGatePassed(false);
        BizException exception = assertThrows(BizException.class,
                () -> service.generate(command("2026-11", "MONTH"), sales(7L)));
        assertEquals(CrmErrorCode.GATE_CLOSED, exception.getCode());
        verify(snapshotRepository, never()).insert(any());
    }

    @Test
    @DisplayName("周期非法：月周期必须 yyyy-MM")
    void shouldRejectIllegalPeriod() {
        BizException exception = assertThrows(BizException.class,
                () -> service.generate(command("2026-13", "MONTH"), sales(7L)));
        assertEquals(CrmErrorCode.FORECAST_PERIOD_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("个人预测：按阶段与概率汇总四类金额")
    void shouldAggregateForecast() {
        when(opportunityRepository.list(any())).thenReturn(List.of(
                opportunity(OpportunityStage.CONTACT, "100", "10"),
                opportunity(OpportunityStage.NEGOTIATION, "200", "60"),
                opportunity(OpportunityStage.WON, "300", "100"),
                opportunity(OpportunityStage.LOST, "999", "0")));
        doAnswer(invocation -> {
            ForecastSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(11L);
            return null;
        }).when(snapshotRepository).insert(any());

        ForecastSnapshotView view = service.generate(command("2026-11", "MONTH"), sales(7L));

        assertEquals(11L, view.getId());
        assertEquals(new BigDecimal("300.00"), view.getPipelineAmount());
        assertEquals(new BigDecimal("200.00"), view.getBestAmount());
        assertEquals(new BigDecimal("300.00"), view.getCommitAmount());
        assertEquals(new BigDecimal("430.00"), view.getWeightedAmount());
        assertEquals(2, view.getOpportunityCount());
        assertEquals(7L, view.getOwnerId());
        assertEquals(Boolean.FALSE, view.getManualAdjusted());
    }

    @Test
    @DisplayName("重算预测：同周期同范围覆盖旧快照")
    void shouldOverwriteExistingSnapshot() {
        ForecastSnapshot existing = snapshot(5L, "2026-11", ForecastScopeType.USER, 7L, 0L);
        existing.setPipelineAmount(new BigDecimal("888.00"));
        when(opportunityRepository.list(any())).thenReturn(List.of(
                opportunity(OpportunityStage.PROPOSAL, "120", "30")));
        when(snapshotRepository.findByPeriodScope(any(), any(), any(), any(), any())).thenReturn(existing);
        when(snapshotRepository.update(any())).thenReturn(true);

        ForecastSnapshotView view = service.generate(command("2026-11", "MONTH"), sales(7L));

        assertEquals(5L, view.getId());
        assertEquals(new BigDecimal("120.00"), view.getPipelineAmount());
        assertEquals(new BigDecimal("36.00"), view.getWeightedAmount());
        verify(snapshotRepository).update(any());
        verify(snapshotRepository, never()).insert(any());
    }

    @Test
    @DisplayName("部门预测：普通销售无权生成")
    void shouldRejectDeptForecastForSales() {
        ForecastGenerateCommand command = command("2026-Q4", "QUARTER");
        command.setScopeType("DEPT");
        command.setDeptId(3L);

        BizException exception = assertThrows(BizException.class,
                () -> service.generate(command, sales(7L)));

        assertEquals(CrmErrorCode.DATA_SCOPE_DENIED, exception.getCode());
    }

    @Test
    @DisplayName("季周期：起止日期覆盖整季")
    void shouldResolveQuarterRange() {
        ForecastPeriodType type = ForecastPeriodType.QUARTER;
        assertEquals(LocalDate.of(2026, 10, 1), type.startDate("2026-Q4"));
        assertEquals(LocalDate.of(2026, 12, 31), type.endDate("2026-Q4"));
    }

    @Test
    @DisplayName("手工调整：写入调整记录并保留前后金额")
    void shouldRecordAdjustment() {
        ForecastSnapshot snapshot = snapshot(5L, "2026-11", ForecastScopeType.USER, 7L, 0L);
        snapshot.setPipelineAmount(new BigDecimal("300.00"));
        when(snapshotRepository.findById(5L)).thenReturn(snapshot);
        when(snapshotRepository.update(any())).thenReturn(true);

        ForecastAdjustCommand command = new ForecastAdjustCommand();
        command.setFieldCode("pipeline");
        command.setAmount(new BigDecimal("500"));
        command.setReason("补充大客户预估");

        ForecastSnapshotView view = service.adjust(5L, command, sales(7L));

        assertEquals(new BigDecimal("500.00"), view.getPipelineAmount());
        assertEquals(Boolean.TRUE, view.getManualAdjusted());
        ArgumentCaptor<ForecastAdjustment> captor = ArgumentCaptor.forClass(ForecastAdjustment.class);
        verify(adjustmentRepository).insert(captor.capture());
        assertEquals(new BigDecimal("300.00"), captor.getValue().getBeforeAmount());
        assertEquals(new BigDecimal("500.00"), captor.getValue().getAfterAmount());
        assertEquals("PIPELINE", captor.getValue().getFieldCode());
        assertEquals(7L, captor.getValue().getOperatorId());
    }

    @Test
    @DisplayName("手工调整：不支持的字段拒绝")
    void shouldRejectUnknownAdjustField() {
        ForecastSnapshot snapshot = snapshot(5L, "2026-11", ForecastScopeType.USER, 7L, 0L);
        when(snapshotRepository.findById(5L)).thenReturn(snapshot);
        ForecastAdjustCommand command = new ForecastAdjustCommand();
        command.setFieldCode("WEIGHTED");
        command.setAmount(new BigDecimal("1"));
        command.setReason("无效字段");

        BizException exception = assertThrows(BizException.class,
                () -> service.adjust(5L, command, sales(7L)));

        assertEquals(CrmErrorCode.FORECAST_ADJUST_INVALID, exception.getCode());
        verify(snapshotRepository, never()).update(any());
    }

    @Test
    @DisplayName("详情：普通销售看不到他人的快照")
    void shouldHideOtherOwnerSnapshot() {
        when(snapshotRepository.findById(5L)).thenReturn(
                snapshot(5L, "2026-11", ForecastScopeType.USER, 8L, 0L));

        BizException exception = assertThrows(BizException.class,
                () -> service.detail(5L, sales(7L)));

        assertEquals(CrmErrorCode.FORECAST_NOT_FOUND, exception.getCode());
    }

    /**
     * 构造预测命令。
     *
     * @param period 周期
     * @param periodType 周期类型
     * @return 命令
     */
    private ForecastGenerateCommand command(String period, String periodType) {
        ForecastGenerateCommand command = new ForecastGenerateCommand();
        command.setPeriod(period);
        command.setPeriodType(periodType);
        command.setScopeType("USER");
        return command;
    }

    /**
     * 构造销售操作人。
     *
     * @param userId 用户 ID
     * @return 操作人
     */
    private CrmActor sales(Long userId) {
        return new CrmActor(userId, "sales", Set.of("crm_sales"), Set.of("sys:crm:forecast:list"));
    }

    /**
     * 构造快照。
     *
     * @param id 快照 ID
     * @param period 周期
     * @param scopeType 范围类型
     * @param ownerId 负责人
     * @param deptId 部门
     * @return 快照
     */
    private ForecastSnapshot snapshot(Long id, String period, ForecastScopeType scopeType,
                                      Long ownerId, Long deptId) {
        ForecastSnapshot snapshot = new ForecastSnapshot();
        snapshot.setId(id);
        snapshot.setPeriod(period);
        snapshot.setPeriodType(ForecastPeriodType.MONTH);
        snapshot.setScopeType(scopeType);
        snapshot.setOwnerId(ownerId);
        snapshot.setDeptId(deptId);
        snapshot.setManualAdjusted(Boolean.FALSE);
        snapshot.setOpportunityCount(0);
        return snapshot;
    }

    /**
     * 构造商机。
     *
     * @param stage 阶段
     * @param amount 金额
     * @param probability 概率
     * @return 商机
     */
    private Opportunity opportunity(OpportunityStage stage, String amount, String probability) {
        Opportunity opportunity = new Opportunity();
        opportunity.setStage(stage);
        opportunity.setAmount(new BigDecimal(amount));
        opportunity.setProbability(new BigDecimal(probability));
        opportunity.setOwnerId(7L);
        return opportunity;
    }
}

package com.bluewhale.crm.sales.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.sales.application.command.SalesTargetSaveCommand;
import com.bluewhale.crm.sales.application.command.SalesTargetUpdateCommand;
import com.bluewhale.crm.sales.application.view.SalesTargetView;
import com.bluewhale.crm.sales.application.view.TargetCompletionView;
import com.bluewhale.crm.sales.domain.model.ForecastPeriodType;
import com.bluewhale.crm.sales.domain.model.ForecastScopeType;
import com.bluewhale.crm.sales.domain.model.Opportunity;
import com.bluewhale.crm.sales.domain.model.OpportunityStage;
import com.bluewhale.crm.sales.domain.model.SalesTarget;
import com.bluewhale.crm.sales.domain.model.SalesTargetAdjustment;
import com.bluewhale.crm.sales.domain.repository.OpportunityRepository;
import com.bluewhale.crm.sales.domain.repository.SalesTargetAdjustmentRepository;
import com.bluewhale.crm.sales.domain.repository.SalesTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
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
 * {@link SalesTargetAppServiceImpl} 单元测试：目标唯一性、调整留痕与完成率口径。
 *
 * @author BlueWhale
 */
class SalesTargetAppServiceImplTest {

    private SalesTargetRepository targetRepository;

    private SalesTargetAdjustmentRepository adjustmentRepository;

    private OpportunityRepository opportunityRepository;

    private CrmProperties properties;

    private SalesTargetAppServiceImpl service;

    @BeforeEach
    void setUp() {
        targetRepository = mock(SalesTargetRepository.class);
        adjustmentRepository = mock(SalesTargetAdjustmentRepository.class);
        opportunityRepository = mock(OpportunityRepository.class);
        properties = new CrmProperties();
        service = new SalesTargetAppServiceImpl(targetRepository, adjustmentRepository,
                opportunityRepository, properties);
    }

    @Test
    @DisplayName("门禁关闭：目标接口拒绝")
    void shouldRejectWhenGateClosed() {
        properties.setDemandGatePassed(false);
        BizException exception = assertThrows(BizException.class,
                () -> service.create(command("2026-11", "MONTH"), manager(7L)));
        assertEquals(CrmErrorCode.GATE_CLOSED, exception.getCode());
        verify(targetRepository, never()).insert(any());
    }

    @Test
    @DisplayName("新建目标：普通销售无权维护")
    void shouldRejectCreateBySales() {
        BizException exception = assertThrows(BizException.class,
                () -> service.create(command("2026-11", "MONTH"), sales(7L)));
        assertEquals(CrmErrorCode.DATA_SCOPE_DENIED, exception.getCode());
        verify(targetRepository, never()).insert(any());
    }

    @Test
    @DisplayName("新建目标：同周期同范围重复拒绝")
    void shouldRejectDuplicateTarget() {
        when(targetRepository.findByPeriodScope(any(), any(), any(), any(), any()))
                .thenReturn(target(5L, "2026-11", 7L));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(command("2026-11", "MONTH"), manager(7L)));

        assertEquals(CrmErrorCode.TARGET_DUPLICATE, exception.getCode());
    }

    @Test
    @DisplayName("新建目标：季周期规范化为 yyyy-Qn")
    void shouldNormalizeQuarterPeriod() {
        doAnswer(invocation -> {
            SalesTarget target = invocation.getArgument(0);
            target.setId(5L);
            return null;
        }).when(targetRepository).insert(any());

        SalesTargetView view = service.create(command("2026-q4", "QUARTER"), manager(7L));

        assertEquals(5L, view.getId());
        assertEquals("2026-Q4", view.getPeriod());
        assertEquals("QUARTER", view.getPeriodType());
        assertEquals(new BigDecimal("1000.00"), view.getTargetAmount());
    }

    @Test
    @DisplayName("调整目标：记录调整前后金额与原因")
    void shouldRecordAdjustment() {
        SalesTarget target = target(5L, "2026-11", 7L);
        target.setTargetAmount(new BigDecimal("1000.00"));
        when(targetRepository.findById(5L)).thenReturn(target);
        when(targetRepository.update(any())).thenReturn(true);

        SalesTargetUpdateCommand command = new SalesTargetUpdateCommand();
        command.setTargetAmount(new BigDecimal("1200"));
        command.setReason("上调四季度目标");

        SalesTargetView view = service.update(5L, command, manager(7L));

        assertEquals(new BigDecimal("1200.00"), view.getTargetAmount());
        ArgumentCaptor<SalesTargetAdjustment> captor =
                ArgumentCaptor.forClass(SalesTargetAdjustment.class);
        verify(adjustmentRepository).insert(captor.capture());
        assertEquals(new BigDecimal("1000.00"), captor.getValue().getBeforeAmount());
        assertEquals(new BigDecimal("1200.00"), captor.getValue().getAfterAmount());
        assertEquals("上调四季度目标", captor.getValue().getReason());
    }

    @Test
    @DisplayName("调整目标：金额变化必须填写原因")
    void shouldRequireReasonWhenAmountChanged() {
        SalesTarget target = target(5L, "2026-11", 7L);
        target.setTargetAmount(new BigDecimal("1000.00"));
        when(targetRepository.findById(5L)).thenReturn(target);
        SalesTargetUpdateCommand command = new SalesTargetUpdateCommand();
        command.setTargetAmount(new BigDecimal("1200"));

        BizException exception = assertThrows(BizException.class,
                () -> service.update(5L, command, manager(7L)));

        assertEquals(CrmErrorCode.PARAM_INVALID, exception.getCode());
        verify(targetRepository, never()).update(any());
        verify(adjustmentRepository, never()).insert(any());
    }

    @Test
    @DisplayName("完成率：按同期赢单商机金额计算")
    void shouldCalculateCompletionRate() {
        SalesTarget target = target(5L, "2026-11", 7L);
        target.setTargetAmount(new BigDecimal("1000.00"));
        when(targetRepository.findById(5L)).thenReturn(target);
        when(opportunityRepository.list(any())).thenReturn(List.of(
                won("300"), won("200")));

        TargetCompletionView view = service.completion(5L, manager(7L));

        assertEquals(new BigDecimal("500.00"), view.getAchievedAmount());
        assertEquals(new BigDecimal("50.00"), view.getCompletionRate());
        assertEquals(2, view.getAchievedCount());
    }

    @Test
    @DisplayName("详情：普通销售看不到他人目标")
    void shouldHideOtherOwnerTarget() {
        when(targetRepository.findById(5L)).thenReturn(target(5L, "2026-11", 8L));

        BizException exception = assertThrows(BizException.class,
                () -> service.detail(5L, sales(7L)));

        assertEquals(CrmErrorCode.TARGET_NOT_FOUND, exception.getCode());
    }

    /**
     * 构造新建命令。
     *
     * @param period 周期
     * @param periodType 周期类型
     * @return 命令
     */
    private SalesTargetSaveCommand command(String period, String periodType) {
        SalesTargetSaveCommand command = new SalesTargetSaveCommand();
        command.setPeriod(period);
        command.setPeriodType(periodType);
        command.setScopeType("USER");
        command.setOwnerId(7L);
        command.setTargetAmount(new BigDecimal("1000"));
        return command;
    }

    /**
     * 构造销售操作人。
     *
     * @param userId 用户 ID
     * @return 操作人
     */
    private CrmActor sales(Long userId) {
        return new CrmActor(userId, "sales", Set.of("crm_sales"), Set.of("sys:crm:target:list"));
    }

    /**
     * 构造主管操作人。
     *
     * @param userId 用户 ID
     * @return 操作人
     */
    private CrmActor manager(Long userId) {
        return new CrmActor(userId, "manager", Set.of("crm_sales_manager"),
                Set.of("sys:crm:target:manage"));
    }

    /**
     * 构造销售目标。
     *
     * @param id 目标 ID
     * @param period 周期
     * @param ownerId 负责人
     * @return 目标
     */
    private SalesTarget target(Long id, String period, Long ownerId) {
        SalesTarget target = new SalesTarget();
        target.setId(id);
        target.setPeriod(period);
        target.setPeriodType(ForecastPeriodType.MONTH);
        target.setScopeType(ForecastScopeType.USER);
        target.setOwnerId(ownerId);
        target.setDeptId(0L);
        return target;
    }

    /**
     * 构造赢单商机。
     *
     * @param amount 金额
     * @return 商机
     */
    private Opportunity won(String amount) {
        Opportunity opportunity = new Opportunity();
        opportunity.setStage(OpportunityStage.WON);
        opportunity.setAmount(new BigDecimal(amount));
        opportunity.setProbability(new BigDecimal("100"));
        return opportunity;
    }
}

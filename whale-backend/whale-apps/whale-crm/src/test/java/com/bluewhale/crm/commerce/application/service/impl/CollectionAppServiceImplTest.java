package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.application.command.CollectionActionCommand;
import com.bluewhale.crm.commerce.application.command.CollectionInstallmentCommand;
import com.bluewhale.crm.commerce.application.command.CollectionPlanSaveCommand;
import com.bluewhale.crm.commerce.application.command.CollectionPromiseCommand;
import com.bluewhale.crm.commerce.application.view.CollectionPlanView;
import com.bluewhale.crm.commerce.domain.model.CollectionAction;
import com.bluewhale.crm.commerce.domain.model.CollectionPlan;
import com.bluewhale.crm.commerce.domain.model.CollectionPlanStatus;
import com.bluewhale.crm.commerce.domain.model.CollectionPriority;
import com.bluewhale.crm.commerce.domain.model.ContractLedger;
import com.bluewhale.crm.commerce.domain.repository.CollectionActionRepository;
import com.bluewhale.crm.commerce.domain.repository.CollectionPlanRepository;
import com.bluewhale.crm.commerce.domain.repository.ContractLedgerRepository;
import com.bluewhale.crm.config.CrmProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CollectionAppServiceImpl} 单元测试：分期合计校验、承诺还款与优先级派生。
 *
 * @author BlueWhale
 */
class CollectionAppServiceImplTest {

    private CollectionPlanRepository planRepository;

    private CollectionActionRepository actionRepository;

    private ContractLedgerRepository contractRepository;

    private CollectionAppServiceImpl service;

    @BeforeEach
    void setUp() {
        planRepository = mock(CollectionPlanRepository.class);
        actionRepository = mock(CollectionActionRepository.class);
        contractRepository = mock(ContractLedgerRepository.class);
        service = new CollectionAppServiceImpl(planRepository, actionRepository, contractRepository,
                new CrmProperties());
    }

    @Test
    @DisplayName("保存分期：合计等于合同金额时按合同整批替换")
    void shouldSavePlanWhenTotalMatchesContract() {
        when(contractRepository.findById(1L)).thenReturn(contract("100.00"));
        when(planRepository.replaceByContract(anyLong(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(4));

        List<CollectionPlanView> saved = service.savePlan(command("40.00", "60.00"), actor());

        assertEquals(2, saved.size());
        assertEquals(1, saved.get(0).getInstallmentNo());
        assertEquals(2, saved.get(1).getInstallmentNo());
        verify(planRepository).replaceByContract(eq(1L), eq(11L), eq(7L), eq(9L), any());
    }

    @Test
    @DisplayName("保存分期：合计与合同金额不一致时拒绝")
    void shouldRejectPlanWhenTotalMismatch() {
        when(contractRepository.findById(1L)).thenReturn(contract("100.00"));

        BizException exception = assertThrows(BizException.class,
                () -> service.savePlan(command("40.00", "50.00"), actor()));
        assertEquals(CrmErrorCode.CONTRACT_AMOUNT_MISMATCH, exception.getCode());
        verify(planRepository, never()).replaceByContract(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("保存分期：合同金额未同步时拒绝，不伪造合计通过")
    void shouldRejectPlanWhenContractAmountUnknown() {
        when(contractRepository.findById(1L)).thenReturn(contract(null));

        BizException exception = assertThrows(BizException.class,
                () -> service.savePlan(command("40.00", "60.00"), actor()));
        assertEquals(CrmErrorCode.CONTRACT_AMOUNT_MISMATCH, exception.getCode());
    }

    @Test
    @DisplayName("承诺还款：更新计划并追加 PROMISE 催收记录")
    void shouldRecordPromiseAction() {
        CollectionPlan plan = openPlan();
        when(planRepository.findById(2L)).thenReturn(plan);
        when(planRepository.updatePromise(any())).thenReturn(true);
        CollectionPromiseCommand command = new CollectionPromiseCommand();
        command.setPromiseDate(LocalDate.now().plusDays(10));
        command.setPromiseAmount(new BigDecimal("30.00"));
        command.setRemark("客户承诺月底前支付");

        CollectionPlanView view = service.promise(2L, command, actor());

        assertEquals(new BigDecimal("30.00"), view.getPromiseAmount());
        ArgumentCaptor<CollectionAction> captor = ArgumentCaptor.forClass(CollectionAction.class);
        verify(actionRepository).insert(captor.capture());
        assertEquals("PROMISE", captor.getValue().getActionType());
        assertEquals(7L, captor.getValue().getOperatorId());
        assertNotNull(captor.getValue().getPromiseDate());
    }

    @Test
    @DisplayName("催收记录：非法动作类型拒绝")
    void shouldRejectUnknownActionType() {
        when(planRepository.findById(2L)).thenReturn(openPlan());
        CollectionActionCommand command = new CollectionActionCommand();
        command.setActionType("UNKNOWN");

        BizException exception = assertThrows(BizException.class,
                () -> service.addAction(2L, command, actor()));
        assertEquals(CrmErrorCode.PARAM_INVALID, exception.getCode());
        verify(actionRepository, never()).insert(any());
    }

    @Test
    @DisplayName("优先级：逾期超过 30 天为 HIGH，超过 7 天为 MEDIUM")
    void shouldDerivePriorityByOverdueDays() {
        CollectionPlan high = openPlan();
        high.setPlannedDate(LocalDate.now().minusDays(40));
        CollectionPlan medium = openPlan();
        medium.setPlannedDate(LocalDate.now().minusDays(10));
        CollectionPlan low = openPlan();
        low.setPlannedDate(LocalDate.now().plusDays(10));

        assertEquals(CollectionPriority.HIGH, high.priority());
        assertEquals(CollectionPriority.MEDIUM, medium.priority());
        assertEquals(CollectionPriority.LOW, low.priority());
    }

    @Test
    @DisplayName("优先级：人工覆盖优先于逾期规则")
    void shouldUsePriorityOverride() {
        CollectionPlan plan = openPlan();
        plan.setPlannedDate(LocalDate.now().plusDays(10));
        plan.setPriorityOverride("HIGH");

        assertEquals(CollectionPriority.HIGH, plan.priority());
    }

    /**
     * 构造合同台账。
     *
     * @param amount 合同金额文本，空表示未同步
     * @return 合同台账
     */
    private ContractLedger contract(String amount) {
        ContractLedger ledger = new ContractLedger();
        ledger.setId(1L);
        ledger.setCustomerId(11L);
        ledger.setOwnerId(7L);
        ledger.setDeptId(9L);
        ledger.setAmount(amount == null ? null : new BigDecimal(amount));
        return ledger;
    }

    /**
     * 构造回款计划。
     *
     * @return 进行中的计划
     */
    private CollectionPlan openPlan() {
        CollectionPlan plan = new CollectionPlan();
        plan.setId(2L);
        plan.setContractId(1L);
        plan.setCustomerId(11L);
        plan.setInstallmentNo(1);
        plan.setPlannedAmount(new BigDecimal("100.00"));
        plan.setPlannedDate(LocalDate.now().plusDays(5));
        plan.setStatus(CollectionPlanStatus.OPEN);
        plan.setOwnerId(7L);
        return plan;
    }

    /**
     * 构造保存命令。
     *
     * @param amounts 分期金额
     * @return 保存命令
     */
    private CollectionPlanSaveCommand command(String... amounts) {
        CollectionPlanSaveCommand command = new CollectionPlanSaveCommand();
        command.setContractId(1L);
        command.setInstallments(java.util.Arrays.stream(amounts).map(amount -> {
            CollectionInstallmentCommand installment = new CollectionInstallmentCommand();
            installment.setPlannedAmount(new BigDecimal(amount));
            installment.setPlannedDate(LocalDate.now().plusMonths(1));
            return installment;
        }).toList());
        return command;
    }

    /**
     * 构造销售操作人。
     *
     * @return 操作人
     */
    private CrmActor actor() {
        return new CrmActor(7L, "sales", Set.of("crm_sales"), Set.of());
    }
}

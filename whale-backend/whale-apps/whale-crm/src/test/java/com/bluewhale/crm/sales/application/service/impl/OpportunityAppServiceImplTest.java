package com.bluewhale.crm.sales.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerDetailView;
import com.bluewhale.crm.sales.application.command.OpportunitySaveCommand;
import com.bluewhale.crm.sales.application.command.OpportunityStageCommand;
import com.bluewhale.crm.sales.application.view.FunnelStageView;
import com.bluewhale.crm.sales.application.view.FunnelView;
import com.bluewhale.crm.sales.application.view.OpportunityView;
import com.bluewhale.crm.sales.domain.model.Opportunity;
import com.bluewhale.crm.sales.domain.model.OpportunityQuery;
import com.bluewhale.crm.sales.domain.model.OpportunityStage;
import com.bluewhale.crm.sales.domain.repository.OpportunityRepository;
import com.bluewhale.crm.sales.domain.repository.OpportunityStageHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpportunityAppServiceImpl} 单元测试：阶段机、金额 / 原因校验、乐观锁与漏斗聚合。
 *
 * @author BlueWhale
 */
class OpportunityAppServiceImplTest {

    private OpportunityRepository opportunityRepository;

    private OpportunityStageHistoryRepository historyRepository;

    private CustomerAppService customerAppService;

    private CrmProperties properties;

    private OpportunityAppServiceImpl service;

    @BeforeEach
    void setUp() {
        opportunityRepository = mock(OpportunityRepository.class);
        historyRepository = mock(OpportunityStageHistoryRepository.class);
        customerAppService = mock(CustomerAppService.class);
        properties = new CrmProperties();
        service = new OpportunityAppServiceImpl(opportunityRepository, historyRepository,
                customerAppService, properties);
    }

    @Test
    @DisplayName("新建商机：必须指定客户")
    void shouldRequireCustomer() {
        OpportunitySaveCommand command = command(null);
        BizException exception = assertThrows(BizException.class,
                () -> service.create(command, sales(7L)));
        assertEquals(CrmErrorCode.OPPORTUNITY_CUSTOMER_REQUIRED, exception.getCode());
        verify(opportunityRepository, never()).insert(any());
    }

    @Test
    @DisplayName("新建商机：初始接触阶段、默认 10% 概率并写入首条阶段历史")
    void shouldCreateWithContactStage() {
        when(customerAppService.detail(anyLong(), any())).thenReturn(new CustomerDetailView());

        OpportunityView view = service.create(command(100L), sales(7L));

        assertEquals("CONTACT", view.getStage());
        assertEquals(new BigDecimal("10"), view.getProbability());
        assertEquals(7L, view.getOwnerId());
        verify(opportunityRepository).insert(any());
        verify(historyRepository).insert(any());
    }

    @Test
    @DisplayName("新建商机：显式概率超出 0–100 被拒绝")
    void shouldRejectOutOfRangeProbability() {
        when(customerAppService.detail(anyLong(), any())).thenReturn(new CustomerDetailView());
        OpportunitySaveCommand command = command(100L);
        command.setProbability(new BigDecimal("120"));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(command, sales(7L)));
        assertEquals(CrmErrorCode.PARAM_INVALID, exception.getCode());
        verify(opportunityRepository, never()).insert(any());
    }

    @Test
    @DisplayName("编辑商机：已关闭商机不可编辑")
    void shouldRejectUpdateOnClosedOpportunity() {
        when(opportunityRepository.findById(1L)).thenReturn(opportunity(1L, 7L, OpportunityStage.WON));

        BizException exception = assertThrows(BizException.class,
                () -> service.update(1L, command(null), sales(7L)));
        assertEquals(CrmErrorCode.OPPORTUNITY_STAGE_CONFLICT, exception.getCode());
        verify(opportunityRepository, never()).update(any());
    }

    @Test
    @DisplayName("编辑商机：乐观锁失败提示并发冲突")
    void shouldReportOptimisticLockConflict() {
        when(opportunityRepository.findById(1L))
                .thenReturn(opportunity(1L, 7L, OpportunityStage.PROPOSAL));
        when(opportunityRepository.update(any())).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> service.update(1L, command(null), sales(7L)));
        assertEquals(CrmErrorCode.OPPORTUNITY_STAGE_CONFLICT, exception.getCode());
        assertTrue(exception.getMessage().contains("刷新"));
    }

    @Test
    @DisplayName("阶段流转：接触转方案成功并记录历史")
    void shouldMoveFromContactToProposal() {
        when(opportunityRepository.findById(1L))
                .thenReturn(opportunity(1L, 7L, OpportunityStage.CONTACT));
        when(opportunityRepository.update(any())).thenReturn(true);

        OpportunityView view = service.changeStage(1L, stageCommand("PROPOSAL"), sales(7L));

        assertEquals("PROPOSAL", view.getStage());
        assertEquals(new BigDecimal("30"), view.getProbability());
        verify(historyRepository).insert(any());
    }

    @Test
    @DisplayName("阶段流转：不允许跨阶段推进")
    void shouldRejectStageSkip() {
        when(opportunityRepository.findById(1L))
                .thenReturn(opportunity(1L, 7L, OpportunityStage.CONTACT));

        BizException exception = assertThrows(BizException.class,
                () -> service.changeStage(1L, stageCommand("WON"), sales(7L)));
        assertEquals(CrmErrorCode.OPPORTUNITY_STAGE_CONFLICT, exception.getCode());
        verify(opportunityRepository, never()).update(any());
    }

    @Test
    @DisplayName("阶段流转：赢单必须金额大于零")
    void shouldRequireAmountBeforeWon() {
        Opportunity opportunity = opportunity(1L, 7L, OpportunityStage.NEGOTIATION);
        opportunity.setAmount(null);
        when(opportunityRepository.findById(1L)).thenReturn(opportunity);

        BizException exception = assertThrows(BizException.class,
                () -> service.changeStage(1L, stageCommand("WON"), sales(7L)));
        assertEquals(CrmErrorCode.OPPORTUNITY_AMOUNT_REQUIRED, exception.getCode());
        verify(opportunityRepository, never()).update(any());
    }

    @Test
    @DisplayName("阶段流转：输单必须填写原因")
    void shouldRequireLostReason() {
        when(opportunityRepository.findById(1L))
                .thenReturn(opportunity(1L, 7L, OpportunityStage.PROPOSAL));

        BizException exception = assertThrows(BizException.class,
                () -> service.changeStage(1L, stageCommand("LOST"), sales(7L)));
        assertEquals(CrmErrorCode.OPPORTUNITY_LOST_REASON_REQUIRED, exception.getCode());
        verify(opportunityRepository, never()).update(any());
    }

    @Test
    @DisplayName("数据范围：普通销售访问他人商机按不存在处理，管理者可见")
    void shouldHideOtherOwnersOpportunity() {
        when(opportunityRepository.findById(1L))
                .thenReturn(opportunity(1L, 7L, OpportunityStage.CONTACT));

        BizException exception = assertThrows(BizException.class,
                () -> service.detail(1L, sales(8L)));
        assertEquals(CrmErrorCode.OPPORTUNITY_NOT_FOUND, exception.getCode());
        assertEquals("CONTACT", service.detail(1L, manager(8L)).getStage());
    }

    @Test
    @DisplayName("漏斗：按阶段聚合数量 / 金额并计算环比转化率")
    void shouldAggregateFunnel() {
        when(opportunityRepository.list(any())).thenReturn(List.of(
                opportunity(1L, 7L, OpportunityStage.CONTACT, "500"),
                opportunity(2L, 7L, OpportunityStage.CONTACT, "500"),
                opportunity(3L, 7L, OpportunityStage.PROPOSAL, "300"),
                opportunity(4L, 7L, OpportunityStage.PROPOSAL, "300"),
                opportunity(5L, 7L, OpportunityStage.NEGOTIATION, "400"),
                opportunity(6L, 7L, OpportunityStage.WON, "300"),
                opportunity(7L, 7L, OpportunityStage.LOST, "100")));

        FunnelView funnel = service.funnel(new OpportunityQuery(), manager(8L));

        assertEquals(List.of("CONTACT", "PROPOSAL", "NEGOTIATION", "WON"),
                funnel.getStages().stream().map(FunnelStageView::getStage).toList());
        assertEquals(2, funnel.getStages().get(0).getCount());
        assertEquals(new BigDecimal("100.00"), funnel.getStages().get(1).getConversionRate());
        assertEquals(new BigDecimal("50.00"), funnel.getStages().get(2).getConversionRate());
        assertEquals(6, funnel.getTotalCount());
        assertTrue(funnel.getTotalAmount().compareTo(new BigDecimal("2300")) == 0);
        assertEquals(1, funnel.getLostCount());
    }

    @Test
    @DisplayName("漏斗：普通销售仅统计本人商机")
    void shouldScopeFunnelToOwner() {
        when(opportunityRepository.list(any())).thenReturn(List.of());
        OpportunityQuery query = new OpportunityQuery();
        query.setOwnerId(99L);

        service.funnel(query, sales(7L));

        assertEquals(7L, query.getOwnerId());
    }

    @Test
    @DisplayName("门禁关闭：商机接口不可用")
    void shouldRejectWhenGateClosed() {
        properties.setDemandGatePassed(false);

        BizException exception = assertThrows(BizException.class,
                () -> service.page(new OpportunityQuery(), manager(8L)));
        assertEquals(CrmErrorCode.GATE_CLOSED, exception.getCode());
    }

    private static OpportunitySaveCommand command(Long customerId) {
        OpportunitySaveCommand command = new OpportunitySaveCommand();
        command.setCustomerId(customerId);
        command.setName("新一代协同平台");
        command.setAmount(new BigDecimal("800000"));
        command.setSource("WEB");
        return command;
    }

    private static OpportunityStageCommand stageCommand(String stage) {
        OpportunityStageCommand command = new OpportunityStageCommand();
        command.setStage(stage);
        return command;
    }

    private static Opportunity opportunity(Long id, Long ownerId, OpportunityStage stage) {
        return opportunity(id, ownerId, stage, "100000");
    }

    private static Opportunity opportunity(Long id, Long ownerId, OpportunityStage stage, String amount) {
        Opportunity opportunity = new Opportunity();
        opportunity.setId(id);
        opportunity.setCustomerId(100L);
        opportunity.setName("商机" + id);
        opportunity.setAmount(new BigDecimal(amount));
        opportunity.setStage(stage);
        opportunity.setProbability(stage.defaultProbability());
        opportunity.setOwnerId(ownerId);
        return opportunity;
    }

    private static CrmActor sales(Long userId) {
        return new CrmActor(userId, "sales", Set.of("crm_sales"), Set.of());
    }

    private static CrmActor manager(Long userId) {
        return new CrmActor(userId, "manager", Set.of("crm_sales_manager"), Set.of());
    }
}

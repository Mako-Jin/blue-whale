package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.application.command.QuoteApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.command.QuoteLineCommand;
import com.bluewhale.crm.commerce.application.command.QuoteSaveCommand;
import com.bluewhale.crm.commerce.application.view.QuoteView;
import com.bluewhale.crm.commerce.domain.model.Quote;
import com.bluewhale.crm.commerce.domain.model.QuoteApproval;
import com.bluewhale.crm.commerce.domain.model.QuoteApprovalStatus;
import com.bluewhale.crm.commerce.domain.model.QuoteLine;
import com.bluewhale.crm.commerce.domain.model.QuoteQuery;
import com.bluewhale.crm.commerce.domain.model.QuoteStatus;
import com.bluewhale.crm.commerce.domain.port.QuoteApprovalPort;
import com.bluewhale.crm.commerce.domain.repository.QuoteApprovalRepository;
import com.bluewhale.crm.commerce.domain.repository.QuoteRepository;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.config.CrmQuoteApprovalProperties;
import com.bluewhale.crm.sales.application.service.OpportunityAppService;
import com.bluewhale.crm.sales.application.view.OpportunityView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QuoteAppServiceImpl} 单元测试：金额口径、状态机、折扣审批层级、回调幂等与版本追加。
 *
 * @author BlueWhale
 */
class QuoteAppServiceImplTest {

    private QuoteRepository quoteRepository;

    private QuoteApprovalRepository approvalRepository;

    private OpportunityAppService opportunityAppService;

    private QuoteApprovalPort approvalPort;

    private CrmProperties properties;

    private CrmQuoteApprovalProperties approvalProperties;

    private QuoteAppServiceImpl service;

    @BeforeEach
    void setUp() {
        quoteRepository = mock(QuoteRepository.class);
        approvalRepository = mock(QuoteApprovalRepository.class);
        opportunityAppService = mock(OpportunityAppService.class);
        approvalPort = mock(QuoteApprovalPort.class);
        properties = new CrmProperties();
        approvalProperties = new CrmQuoteApprovalProperties();
        approvalProperties.setEnabled(true);
        approvalProperties.setDefinitionId(900L);
        service = new QuoteAppServiceImpl(quoteRepository, approvalRepository, opportunityAppService,
                approvalPort, properties, approvalProperties);
    }

    @Test
    @DisplayName("门禁关闭：报价接口拒绝")
    void shouldRejectWhenGateClosed() {
        properties.setDemandGatePassed(false);
        BizException exception = assertThrows(BizException.class,
                () -> service.create(command(), sales(7L)));
        assertEquals(CrmErrorCode.GATE_CLOSED, exception.getCode());
        verify(quoteRepository, never()).insert(any());
    }

    @Test
    @DisplayName("新建报价：缺少明细行拒绝")
    void shouldRequireLines() {
        QuoteSaveCommand command = command();
        command.setLines(List.of());
        BizException exception = assertThrows(BizException.class,
                () -> service.create(command, sales(7L)));
        assertEquals(CrmErrorCode.QUOTE_LINE_REQUIRED, exception.getCode());
        verify(quoteRepository, never()).insert(any());
    }

    @Test
    @DisplayName("新建报价：取商机客户、生成编号、首版绑定根版本")
    void shouldCreateFirstVersion() {
        when(opportunityAppService.detail(anyLong(), any())).thenReturn(opportunity());
        when(quoteRepository.maxSequence(any())).thenReturn(41L);
        doAnswer(invocation -> {
            Quote quote = invocation.getArgument(0);
            quote.setId(99L);
            return null;
        }).when(quoteRepository).insert(any());

        QuoteView view = service.create(command(), sales(7L));

        assertEquals(99L, view.getId());
        assertEquals(1, view.getVersion());
        assertEquals(99L, view.getRootQuoteId());
        assertEquals(22L, view.getCustomerId());
        assertEquals(7L, view.getOwnerId());
        assertEquals("DRAFT", view.getStatus());
        assertTrue(view.getQuoteNo().startsWith("QUOTE-" + LocalDate.now().getYear() + "-000042"));
        assertEquals(new BigDecimal("180.00"), view.getSubtotalAmount());
        assertEquals(new BigDecimal("23.40"), view.getTaxAmount());
        assertEquals(new BigDecimal("203.40"), view.getTotalAmount());
        assertEquals(new BigDecimal("10.00"), view.getDiscountRate());
        verify(quoteRepository).bindRoot(99L, 99L);
        verify(quoteRepository).replaceLines(anyLong(), any());
    }

    @Test
    @DisplayName("新建报价：整单折扣超过行合计加税额拒绝")
    void shouldRejectExcessiveDiscount() {
        when(opportunityAppService.detail(anyLong(), any())).thenReturn(opportunity());
        QuoteSaveCommand command = command();
        command.setDiscountAmount(new BigDecimal("9999"));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(command, sales(7L)));
        assertEquals(CrmErrorCode.QUOTE_AMOUNT_INVALID, exception.getCode());
        verify(quoteRepository, never()).insert(any());
    }

    @Test
    @DisplayName("编辑报价：已通过报价锁定只读")
    void shouldRejectUpdateOnApprovedQuote() {
        when(quoteRepository.findById(1L)).thenReturn(quote(1L, 7L, QuoteStatus.APPROVED));

        BizException exception = assertThrows(BizException.class,
                () -> service.update(1L, command(), sales(7L)));
        assertEquals(CrmErrorCode.QUOTE_STATE_CONFLICT, exception.getCode());
        verify(quoteRepository, never()).update(any());
    }

    @Test
    @DisplayName("提交审批：审批未启用时失败关闭")
    void shouldRejectSubmitWhenApprovalDisabled() {
        approvalProperties.setEnabled(false);
        when(quoteRepository.findById(1L)).thenReturn(quote(1L, 7L, QuoteStatus.DRAFT));

        BizException exception = assertThrows(BizException.class,
                () -> service.submit(1L, sales(7L)));
        assertEquals(CrmErrorCode.QUOTE_APPROVAL_UNAVAILABLE, exception.getCode());
        verify(approvalPort, never()).start(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("提交审批：≤5% 走主管，流程变量透传")
    void shouldSubmitWithSupervisorLevel() {
        Quote quote = quote(1L, 7L, QuoteStatus.DRAFT);
        quote.setDiscountRate(new BigDecimal("5.00"));
        when(quoteRepository.findById(1L)).thenReturn(quote);
        when(quoteRepository.update(any())).thenReturn(true);
        when(quoteRepository.findLines(1L)).thenReturn(List.of(line()));
        when(approvalPort.start(any(), any(), any(), any(), any())).thenReturn(1234L);

        QuoteView view = service.submit(1L, sales(7L));

        assertEquals("PENDING_APPROVAL", view.getStatus());
        assertEquals(1234L, view.getApprovalInstanceId());
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(approvalPort).start(any(), any(), any(), captor.capture(), any());
        assertEquals("SUPERVISOR", captor.getValue().get("approvalLevel"));
        assertEquals(new BigDecimal("5.00"), captor.getValue().get("discountRate"));
        verify(approvalRepository).insert(any());
    }

    @Test
    @DisplayName("提交审批：20% 折扣走总经理审批")
    void shouldSubmitWithGeneralManagerLevel() {
        Quote quote = quote(1L, 7L, QuoteStatus.DRAFT);
        quote.setDiscountRate(new BigDecimal("20.00"));
        when(quoteRepository.findById(1L)).thenReturn(quote);
        when(quoteRepository.update(any())).thenReturn(true);
        when(quoteRepository.findLines(1L)).thenReturn(List.of(line()));
        when(approvalPort.start(any(), any(), any(), any(), any())).thenReturn(1234L);

        service.submit(1L, sales(7L));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(approvalPort).start(any(), any(), any(), captor.capture(), any());
        assertEquals("GENERAL_MANAGER", captor.getValue().get("approvalLevel"));
    }

    @Test
    @DisplayName("提交审批：工作流发起失败保持草稿")
    void shouldKeepDraftWhenWorkflowFails() {
        Quote quote = quote(1L, 7L, QuoteStatus.DRAFT);
        when(quoteRepository.findById(1L)).thenReturn(quote);
        when(approvalPort.start(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("workflow down"));

        BizException exception = assertThrows(BizException.class,
                () -> service.submit(1L, sales(7L)));
        assertTrue(exception.getMessage().contains("报价审批发起失败"));
        assertEquals(QuoteStatus.DRAFT, quote.getStatus());
        verify(quoteRepository, never()).update(any());
        verify(approvalRepository, never()).insert(any());
    }

    @Test
    @DisplayName("审批回调：通过后报价锁定")
    void shouldApproveOnCallback() {
        Quote quote = quote(1L, 7L, QuoteStatus.PENDING_APPROVAL);
        when(approvalRepository.findPendingByInstanceId(1234L)).thenReturn(approval());
        when(quoteRepository.findById(1L)).thenReturn(quote);
        when(quoteRepository.update(any())).thenReturn(true);

        boolean advanced = service.handleApprovalCallback(callback(1234L, 1));

        assertTrue(advanced);
        assertEquals(QuoteStatus.APPROVED, quote.getStatus());
        verify(approvalRepository).markDecided(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("审批回调：驳回后可改价，重复回调与未知实例幂等返回 false")
    void shouldBeIdempotentOnCallback() {
        Quote quote = quote(1L, 7L, QuoteStatus.PENDING_APPROVAL);
        when(approvalRepository.findPendingByInstanceId(1234L)).thenReturn(approval());
        when(quoteRepository.findById(1L)).thenReturn(quote);
        when(quoteRepository.update(any())).thenReturn(true);

        assertTrue(service.handleApprovalCallback(callback(1234L, 2)));
        assertEquals(QuoteStatus.REJECTED, quote.getStatus());

        when(approvalRepository.findPendingByInstanceId(1234L)).thenReturn(null);
        assertFalse(service.handleApprovalCallback(callback(1234L, 1)));
        assertFalse(service.handleApprovalCallback(callback(9999L, 1)));
        verify(quoteRepository, times(1)).update(any());
    }

    @Test
    @DisplayName("改价：已通过报价生成新版本，根版本与来源指向首版")
    void shouldReviseApprovedQuote() {
        when(quoteRepository.findById(1L)).thenReturn(quote(1L, 7L, QuoteStatus.APPROVED));
        when(quoteRepository.findLines(1L)).thenReturn(List.of(line()));
        doAnswer(invocation -> {
            Quote quote = invocation.getArgument(0);
            quote.setId(100L);
            return null;
        }).when(quoteRepository).insert(any());

        QuoteView view = service.revise(1L, sales(7L));

        assertEquals(100L, view.getId());
        assertEquals(2, view.getVersion());
        assertEquals("DRAFT", view.getStatus());
        assertEquals(1L, view.getRootQuoteId());
        assertEquals(1L, view.getParentQuoteId());
        assertEquals(1, view.getLines().size());
    }

    @Test
    @DisplayName("数据范围：普通销售仅看本人，越权按不存在处理")
    void shouldApplyDataScope() {
        QuoteQuery query = new QuoteQuery();
        service.page(query, sales(7L));
        assertEquals(7L, query.getOwnerId());

        when(quoteRepository.findById(2L)).thenReturn(quote(2L, 8L, QuoteStatus.DRAFT));
        BizException exception = assertThrows(BizException.class,
                () -> service.detail(2L, sales(7L)));
        assertEquals(CrmErrorCode.QUOTE_NOT_FOUND, exception.getCode());

        when(quoteRepository.findById(2L)).thenReturn(quote(2L, 8L, QuoteStatus.DRAFT));
        assertEquals("DRAFT", service.detail(2L, manager(9L)).getStatus());
    }

    /**
     * 构造销售操作人。
     *
     * @param userId 用户 ID
     * @return 操作人
     */
    private CrmActor sales(Long userId) {
        return new CrmActor(userId, "sales", Set.of("crm_sales"), Set.of("sys:crm:quote:create"));
    }

    /**
     * 构造销售主管操作人。
     *
     * @param userId 用户 ID
     * @return 操作人
     */
    private CrmActor manager(Long userId) {
        return new CrmActor(userId, "manager", Set.of("crm_sales_manager"), Set.of("sys:crm:quote:list"));
    }

    /**
     * 构造商机视图。
     *
     * @return 商机视图
     */
    private OpportunityView opportunity() {
        OpportunityView view = new OpportunityView();
        view.setId(11L);
        view.setCustomerId(22L);
        view.setOwnerId(7L);
        view.setDeptId(33L);
        return view;
    }

    /**
     * 构造新建命令：2 件 × 100 元、行折扣 10%、税率 13%。
     *
     * @return 保存命令
     */
    private QuoteSaveCommand command() {
        QuoteSaveCommand command = new QuoteSaveCommand();
        command.setOpportunityId(11L);
        command.setTitle("测试报价");
        command.setValidUntil(LocalDate.now().plusDays(30));
        QuoteLineCommand line = new QuoteLineCommand();
        line.setProductName("蓝色鲸服务");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("100"));
        line.setDiscountRate(new BigDecimal("10"));
        line.setTaxRate(new BigDecimal("13"));
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造报价。
     *
     * @param id 报价 ID
     * @param ownerId 负责人
     * @param status 状态
     * @return 报价
     */
    private Quote quote(Long id, Long ownerId, QuoteStatus status) {
        Quote quote = new Quote();
        quote.setId(id);
        quote.setOwnerId(ownerId);
        quote.setStatus(status);
        quote.setTitle("测试报价");
        quote.setQuoteNo("QUOTE-2026-000001");
        quote.setVersion(1);
        quote.setRootQuoteId(id);
        quote.setOpportunityId(11L);
        quote.setCustomerId(22L);
        quote.setCurrency("CNY");
        quote.setSubtotalAmount(new BigDecimal("180.00"));
        quote.setTaxAmount(new BigDecimal("23.40"));
        quote.setDiscountAmount(BigDecimal.ZERO);
        quote.setTotalAmount(new BigDecimal("203.40"));
        quote.setDiscountRate(new BigDecimal("10.00"));
        quote.setLockVersion(0);
        return quote;
    }

    /**
     * 构造报价行。
     *
     * @return 报价行
     */
    private QuoteLine line() {
        QuoteLine line = new QuoteLine();
        line.setLineNo(1);
        line.setProductName("蓝色鲸服务");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("100"));
        line.setDiscountRate(new BigDecimal("10"));
        line.setTaxRate(new BigDecimal("13"));
        line.setLineAmount(new BigDecimal("180.00"));
        return line;
    }

    /**
     * 构造待决策审批记录。
     *
     * @return 审批记录
     */
    private QuoteApproval approval() {
        QuoteApproval approval = new QuoteApproval();
        approval.setId(77L);
        approval.setQuoteId(1L);
        approval.setQuoteVersion(1);
        approval.setInstanceId(1234L);
        approval.setStatus(QuoteApprovalStatus.PENDING);
        return approval;
    }

    /**
     * 构造回调命令。
     *
     * @param instanceId 流程实例 ID
     * @param status 工作流状态
     * @return 回调命令
     */
    private QuoteApprovalCallbackCommand callback(Long instanceId, Integer status) {
        QuoteApprovalCallbackCommand command = new QuoteApprovalCallbackCommand();
        command.setInstanceId(instanceId);
        command.setStatus(status);
        command.setComment("审批意见");
        return command;
    }
}

package com.bluewhale.crm.service.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.domain.repository.SalesOrderRepository;
import com.bluewhale.crm.config.CrmErpProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.config.CrmReturnApprovalProperties;
import com.bluewhale.crm.config.CrmServiceProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.service.application.command.ReturnApprovalCallbackCommand;
import com.bluewhale.crm.service.domain.model.ReturnOutboxEvent;
import com.bluewhale.crm.service.domain.model.ReturnRequest;
import com.bluewhale.crm.service.domain.model.ReturnRequestItem;
import com.bluewhale.crm.service.domain.model.ReturnRequestQuery;
import com.bluewhale.crm.service.domain.model.ReturnStatus;
import com.bluewhale.crm.service.domain.port.ErpReturnPort;
import com.bluewhale.crm.service.domain.port.ErpReturnResult;
import com.bluewhale.crm.service.domain.port.ReturnApprovalPort;
import com.bluewhale.crm.service.domain.repository.ReturnOutboxRepository;
import com.bluewhale.crm.service.domain.repository.ReturnRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReturnRequestAppServiceImpl} 单元测试：审批回调幂等、下发成功 / 失败退避、对账与开关门禁。
 *
 * @author BlueWhale
 */
class ReturnRequestAppServiceImplTest {

    private ReturnRequestRepository returnRequestRepository;

    private ReturnOutboxRepository returnOutboxRepository;

    private ErpReturnPort erpReturnPort;

    private ReturnApprovalPort returnApprovalPort;

    private CrmProperties properties;

    private CrmServiceProperties serviceProperties;

    private CrmReturnApprovalProperties approvalProperties;

    private CrmErpProperties erpProperties;

    private ReturnRequestAppServiceImpl service;

    @BeforeEach
    void setUp() {
        returnRequestRepository = mock(ReturnRequestRepository.class);
        returnOutboxRepository = mock(ReturnOutboxRepository.class);
        erpReturnPort = mock(ErpReturnPort.class);
        returnApprovalPort = mock(ReturnApprovalPort.class);
        properties = new CrmProperties();
        serviceProperties = new CrmServiceProperties();
        approvalProperties = new CrmReturnApprovalProperties();
        erpProperties = new CrmErpProperties();
        service = new ReturnRequestAppServiceImpl(returnRequestRepository, returnOutboxRepository,
                mock(SalesOrderRepository.class), mock(CustomerAppService.class), erpReturnPort,
                returnApprovalPort, properties, serviceProperties, approvalProperties, erpProperties);
    }

    @Test
    @DisplayName("审批通过：同事务写入下发 outbox")
    void shouldEnqueueOutboxWhenApproved() {
        ReturnRequest request = approvedRequest();
        request.setStatus(ReturnStatus.PENDING_APPROVAL);
        when(returnRequestRepository.findByApprovalInstanceId(88L)).thenReturn(request);
        when(returnRequestRepository.applyApprovalResult(eq(1L), eq(true), any(LocalDateTime.class)))
                .thenReturn(true);

        boolean handled = service.handleApprovalCallback(callback(true));

        assertTrue(handled);
        verify(returnOutboxRepository).enqueue(eq(1L), contains("\"returnId\":1"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("审批回调幂等：非审批中申请不重复入队")
    void shouldIgnoreRepeatedCallback() {
        when(returnRequestRepository.findByApprovalInstanceId(88L)).thenReturn(approvedRequest());
        when(returnRequestRepository.applyApprovalResult(eq(1L), eq(true), any(LocalDateTime.class)))
                .thenReturn(false);

        boolean handled = service.handleApprovalCallback(callback(true));

        assertFalse(handled);
        verify(returnOutboxRepository, never()).enqueue(anyLong(), any(), any());
    }

    @Test
    @DisplayName("审批驳回：不写入下发 outbox")
    void shouldNotEnqueueWhenRejected() {
        when(returnRequestRepository.findByApprovalInstanceId(88L)).thenReturn(approvedRequest());
        when(returnRequestRepository.applyApprovalResult(eq(1L), eq(false), any(LocalDateTime.class)))
                .thenReturn(true);

        service.handleApprovalCallback(callback(false));

        verify(returnOutboxRepository, never()).enqueue(anyLong(), any(), any());
    }

    @Test
    @DisplayName("下发开关关闭：扫描直接返回 0")
    void shouldSkipWhenErpDisabled() {
        assertEquals(0, service.pushPending());
        verify(returnOutboxRepository, never()).findPending(anyInt(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("下发成功：申请转已完成并回写 ERP 退货单号")
    void shouldCompleteAfterPush() {
        erpProperties.setEnabled(true);
        ReturnRequest request = approvedRequest();
        when(returnOutboxRepository.findPending(anyInt(), any(LocalDateTime.class)))
                .thenReturn(List.of(pendingEvent()));
        when(returnRequestRepository.findById(1L)).thenReturn(request);
        when(returnRequestRepository.findItems(1L)).thenReturn(List.of(item()));
        when(erpReturnPort.create(any())).thenReturn(
                new ErpReturnResult(3L, "CRM-SR-RET-20261107-0001", "POSTED", new BigDecimal("40")));

        int pushed = service.pushPending();

        assertEquals(1, pushed);
        assertEquals(ReturnStatus.COMPLETED, request.getStatus());
        assertEquals("CRM-SR-RET-20261107-0001", request.getErpReturnNo());
        assertEquals("POSTED", request.getErpStatus());
        verify(returnOutboxRepository).markSent(9L);
    }

    @Test
    @DisplayName("下发失败：申请转执行失败并按退避标记 outbox 重试")
    void shouldMarkFailedAfterPush() {
        erpProperties.setEnabled(true);
        ReturnRequest request = approvedRequest();
        when(returnOutboxRepository.findPending(anyInt(), any(LocalDateTime.class)))
                .thenReturn(List.of(pendingEvent()));
        when(returnRequestRepository.findById(1L)).thenReturn(request);
        when(returnRequestRepository.findItems(1L)).thenReturn(List.of(item()));
        when(erpReturnPort.create(any())).thenThrow(new IllegalStateException("erp down"));

        int pushed = service.pushPending();

        assertEquals(0, pushed);
        assertEquals(ReturnStatus.FAILED, request.getStatus());
        assertTrue(request.getLastError().contains("erp down"));
        verify(returnOutboxRepository).markFailed(eq(9L), contains("erp down"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("对账：ERP 已生成退货单时采纳单号并关闭 outbox")
    void shouldAdoptErpReturnOnReconcile() {
        erpProperties.setEnabled(true);
        ReturnRequest request = approvedRequest();
        request.setStatus(ReturnStatus.EXECUTING);
        when(returnRequestRepository.findByStatuses(any(), anyInt())).thenReturn(List.of(request));
        when(erpReturnPort.findByCrmReturnNo("RET-20261107-0001")).thenReturn(
                new ErpReturnResult(3L, "CRM-SR-RET-20261107-0001", "POSTED", new BigDecimal("40")));

        int refreshed = service.reconcileAll();

        assertEquals(1, refreshed);
        assertEquals(ReturnStatus.COMPLETED, request.getStatus());
        assertEquals("CRM-SR-RET-20261107-0001", request.getErpReturnNo());
        verify(returnOutboxRepository).cancelByRequestId(1L);
    }

    @Test
    @DisplayName("手动重试：下发失败申请重置 outbox")
    void shouldResetOutboxOnRetry() {
        erpProperties.setEnabled(true);
        ReturnRequest request = approvedRequest();
        request.setStatus(ReturnStatus.FAILED);
        when(returnRequestRepository.findById(1L)).thenReturn(request);

        service.retry(1L, actor());

        verify(returnOutboxRepository).resetForRetry(eq(1L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("手动重试：ERP 开关关闭时拒绝")
    void shouldRejectRetryWhenErpDisabled() {
        ReturnRequest request = approvedRequest();
        request.setStatus(ReturnStatus.FAILED);
        when(returnRequestRepository.findById(1L)).thenReturn(request);

        BizException exception = assertThrows(BizException.class, () -> service.retry(1L, actor()));

        assertEquals(CrmErrorCode.RETURN_ERP_UNAVAILABLE, exception.getCode());
        verify(returnOutboxRepository, never()).resetForRetry(anyLong(), any());
    }

    @Test
    @DisplayName("需求门禁关闭：查询直接拒绝")
    void shouldRejectWhenGateClosed() {
        properties.setDemandGatePassed(false);

        BizException exception = assertThrows(BizException.class,
                () -> service.page(new ReturnRequestQuery(), actor()));

        assertEquals(CrmErrorCode.GATE_CLOSED, exception.getCode());
    }

    /**
     * 构造审批回调命令。
     *
     * @param approved 是否通过
     * @return 命令
     */
    private ReturnApprovalCallbackCommand callback(boolean approved) {
        ReturnApprovalCallbackCommand command = new ReturnApprovalCallbackCommand();
        command.setInstanceId(88L);
        command.setApproved(approved);
        return command;
    }

    /**
     * 构造当前操作人。
     *
     * @return 操作人
     */
    private CrmActor actor() {
        return new CrmActor(5L, "tester", Set.of("crm_admin"), Set.of());
    }

    /**
     * 构造审批通过的申请。
     *
     * @return 申请
     */
    private ReturnRequest approvedRequest() {
        ReturnRequest request = new ReturnRequest();
        request.setId(1L);
        request.setReturnNo("RET-20261107-0001");
        request.setOrderId(10L);
        request.setErpOrderNo("ERP-SO-1");
        request.setCustomerId(21L);
        request.setStatus(ReturnStatus.APPROVED);
        request.setPushAttempts(0);
        return request;
    }

    /**
     * 构造退货申请行。
     *
     * @return 行
     */
    private ReturnRequestItem item() {
        ReturnRequestItem item = new ReturnRequestItem();
        item.setRequestId(1L);
        item.setLineNo(1);
        item.setOrderLineNo(1);
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("20"));
        item.setAmount(new BigDecimal("40"));
        return item;
    }

    /**
     * 构造待下发 outbox 事件。
     *
     * @return 事件
     */
    private ReturnOutboxEvent pendingEvent() {
        ReturnOutboxEvent event = new ReturnOutboxEvent();
        event.setId(9L);
        event.setTenantId(1L);
        event.setAggregateId(1L);
        event.setStatus("PENDING");
        return event;
    }
}

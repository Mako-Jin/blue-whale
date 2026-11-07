package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.crm.commerce.application.command.SalesOrderApprovalCallbackCommand;
import com.bluewhale.crm.commerce.domain.model.OrderOutboxEvent;
import com.bluewhale.crm.commerce.domain.model.SalesOrder;
import com.bluewhale.crm.commerce.domain.model.SalesOrderLine;
import com.bluewhale.crm.commerce.domain.model.SalesOrderStatus;
import com.bluewhale.crm.commerce.domain.port.ErpOrderPort;
import com.bluewhale.crm.commerce.domain.port.OrderApprovalPort;
import com.bluewhale.crm.commerce.domain.repository.ContractLedgerRepository;
import com.bluewhale.crm.commerce.domain.repository.OrderOutboxRepository;
import com.bluewhale.crm.commerce.domain.repository.OrderPushLogRepository;
import com.bluewhale.crm.commerce.domain.repository.QuoteRepository;
import com.bluewhale.crm.commerce.domain.repository.SalesOrderRepository;
import com.bluewhale.crm.config.CrmErpProperties;
import com.bluewhale.crm.config.CrmOrderApprovalProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SalesOrderAppServiceImpl} 单元测试：审批回调幂等、下发失败退避与开关门禁。
 *
 * @author BlueWhale
 */
class SalesOrderAppServiceImplTest {

    private SalesOrderRepository salesOrderRepository;

    private OrderOutboxRepository orderOutboxRepository;

    private ErpOrderPort erpOrderPort;

    private CrmErpProperties erpProperties;

    private SalesOrderAppServiceImpl service;

    @BeforeEach
    void setUp() {
        salesOrderRepository = mock(SalesOrderRepository.class);
        orderOutboxRepository = mock(OrderOutboxRepository.class);
        erpOrderPort = mock(ErpOrderPort.class);
        erpProperties = new CrmErpProperties();
        service = new SalesOrderAppServiceImpl(salesOrderRepository, orderOutboxRepository,
                mock(QuoteRepository.class), mock(ContractLedgerRepository.class),
                mock(CustomerAppService.class), mock(OrderApprovalPort.class), erpOrderPort,
                mock(OrderPushLogRepository.class), new CrmProperties(), new CrmOrderApprovalProperties(),
                erpProperties);
    }

    @Test
    @DisplayName("审批通过：同事务写入下发 outbox")
    void shouldEnqueueOutboxWhenApproved() {
        SalesOrder order = pendingOrder();
        when(salesOrderRepository.findByApprovalInstanceId(88L)).thenReturn(order);
        when(salesOrderRepository.applyApprovalResult(eq(1L), eq(true), any(LocalDateTime.class)))
                .thenReturn(true);

        boolean handled = service.handleApprovalCallback(callback(true));

        assertTrue(handled);
        verify(orderOutboxRepository).enqueue(eq(1L), contains("\"orderId\":1"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("审批回调幂等：非待审批订单不重复入队")
    void shouldIgnoreRepeatedCallback() {
        SalesOrder order = pendingOrder();
        when(salesOrderRepository.findByApprovalInstanceId(88L)).thenReturn(order);
        when(salesOrderRepository.applyApprovalResult(eq(1L), eq(true), any(LocalDateTime.class)))
                .thenReturn(false);

        boolean handled = service.handleApprovalCallback(callback(true));

        assertFalse(handled);
        verify(orderOutboxRepository, never()).enqueue(anyLong(), any(), any());
    }

    @Test
    @DisplayName("审批驳回：不写入下发 outbox")
    void shouldNotEnqueueWhenRejected() {
        when(salesOrderRepository.findByApprovalInstanceId(88L)).thenReturn(pendingOrder());
        when(salesOrderRepository.applyApprovalResult(eq(1L), eq(false), any(LocalDateTime.class)))
                .thenReturn(true);

        service.handleApprovalCallback(callback(false));

        verify(orderOutboxRepository, never()).enqueue(anyLong(), any(), any());
    }

    @Test
    @DisplayName("下发开关关闭：扫描直接返回 0")
    void shouldSkipWhenErpDisabled() {
        assertEquals(0, service.pushPending());
        verify(orderOutboxRepository, never()).findPending(anyInt(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("下发失败：订单转下发失败并按退避标记 outbox 重试")
    void shouldMarkPushFailedAndScheduleRetry() {
        erpProperties.setEnabled(true);
        OrderOutboxEvent event = new OrderOutboxEvent();
        event.setId(11L);
        event.setTenantId(2L);
        event.setAggregateId(1L);
        when(orderOutboxRepository.findPending(eq(100), any(LocalDateTime.class))).thenReturn(List.of(event));
        when(salesOrderRepository.findById(1L)).thenReturn(approvedOrder());
        when(salesOrderRepository.findLines(1L)).thenReturn(List.of(line()));
        when(erpOrderPort.create(any())).thenThrow(new RuntimeException("erp down"));

        assertEquals(0, service.pushPending());

        ArgumentCaptor<SalesOrder> captor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(salesOrderRepository, times(2)).update(captor.capture());
        assertEquals(SalesOrderStatus.PUSH_FAILED, captor.getValue().getStatus());
        verify(orderOutboxRepository).markFailed(eq(11L), contains("erp down"), any(LocalDateTime.class));
    }

    /**
     * 构造审批回调命令。
     *
     * @param approved 是否通过
     * @return 命令
     */
    private SalesOrderApprovalCallbackCommand callback(boolean approved) {
        SalesOrderApprovalCallbackCommand command = new SalesOrderApprovalCallbackCommand();
        command.setInstanceId(88L);
        command.setApproved(approved);
        return command;
    }

    /**
     * 构造待审批订单。
     *
     * @return 订单
     */
    private SalesOrder pendingOrder() {
        SalesOrder order = approvedOrder();
        order.setStatus(SalesOrderStatus.PENDING_APPROVAL);
        return order;
    }

    /**
     * 构造审批通过订单。
     *
     * @return 订单
     */
    private SalesOrder approvedOrder() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setTenantId(2L);
        order.setOrderNo("SO-20261107-0001");
        order.setCustomerId(21L);
        order.setCustomerCode("CUS-001");
        order.setCurrency("CNY");
        order.setStatus(SalesOrderStatus.APPROVED);
        return order;
    }

    /**
     * 构造订单行。
     *
     * @return 订单行
     */
    private SalesOrderLine line() {
        SalesOrderLine line = new SalesOrderLine();
        line.setLineNo(1);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("10"));
        return line;
    }
}

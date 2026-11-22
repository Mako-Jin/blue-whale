package com.bluewhale.pay.service.impl;

import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.config.PayCloseProperties;
import com.bluewhale.pay.enums.PayChannelFeature;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.stat.PayStaleOrderPo;
import com.bluewhale.pay.po.PayOrderPo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PayCloseServiceImpl} 超时关单单元测试：渠道关单成功、失败退避、
 * 渠道不支持关单时直接本地取消。
 *
 * @author BlueWhale
 */
class PayCloseServiceImplTest {

    private final PayOrderMapper payOrderMapper = mock(PayOrderMapper.class);

    private final PayChannelGateway payChannelGateway = mock(PayChannelGateway.class);

    private final PayCloseServiceImpl service = new PayCloseServiceImpl(
            payOrderMapper, payChannelGateway, new PayCloseProperties());

    private static PayStaleOrderPo staleItem(Long id) {
        PayStaleOrderPo item = new PayStaleOrderPo();
        item.setId(id);
        item.setTenantId(7L);
        return item;
    }

    private static PayOrderPo createdOrder(Long id) {
        PayOrderPo order = new PayOrderPo();
        order.setId(id);
        order.setOrderNo("MO" + id);
        order.setChannel("MOCK");
        order.setStatus("CREATED");
        order.setAmount(new BigDecimal("100.00"));
        return order;
    }

    @Test
    @DisplayName("渠道支持关单：先调渠道再条件关单")
    void shouldCloseThroughChannel() {
        PayOrderPo order = createdOrder(1L);
        when(payOrderMapper.selectTimeoutCreated(any(), any(), anyInt()))
                .thenReturn(List.of(staleItem(1L)));
        when(payOrderMapper.selectById(1L)).thenReturn(order);
        when(payChannelGateway.supports(eq("MOCK"), eq(PayChannelFeature.CLOSE))).thenReturn(true);
        when(payChannelGateway.close(order)).thenReturn(new PayChannelResult("MOCK-C1"));
        when(payOrderMapper.closeIfCreated(1L, "TIMEOUT")).thenReturn(1);

        assertEquals(1, service.closeTimeoutOrders());

        verify(payChannelGateway).close(order);
        verify(payOrderMapper).closeIfCreated(1L, "TIMEOUT");
    }

    @Test
    @DisplayName("渠道关单失败：记录退避时间，不置取消、不阻塞本轮")
    void shouldScheduleRetryWhenChannelCloseFails() {
        PayOrderPo order = createdOrder(1L);
        when(payOrderMapper.selectTimeoutCreated(any(), any(), anyInt()))
                .thenReturn(List.of(staleItem(1L)));
        when(payOrderMapper.selectById(1L)).thenReturn(order);
        when(payChannelGateway.supports(eq("MOCK"), eq(PayChannelFeature.CLOSE))).thenReturn(true);
        when(payChannelGateway.close(order)).thenThrow(new IllegalStateException("渠道超时"));

        assertEquals(0, service.closeTimeoutOrders());

        verify(payOrderMapper).markCloseFailure(eq(1L), any(LocalDateTime.class));
        verify(payOrderMapper, never()).closeIfCreated(any(), any());
    }

    @Test
    @DisplayName("渠道不支持关单：直接本地取消并记录 TIMEOUT")
    void shouldCancelLocallyWhenChannelCannotClose() {
        PayOrderPo order = createdOrder(1L);
        when(payOrderMapper.selectTimeoutCreated(any(), any(), anyInt()))
                .thenReturn(List.of(staleItem(1L)));
        when(payOrderMapper.selectById(1L)).thenReturn(order);
        when(payChannelGateway.supports(eq("MOCK"), eq(PayChannelFeature.CLOSE))).thenReturn(false);
        when(payOrderMapper.closeIfCreated(1L, "TIMEOUT")).thenReturn(1);

        assertEquals(1, service.closeTimeoutOrders());

        verify(payChannelGateway, never()).close(any());
        verify(payOrderMapper).closeIfCreated(1L, "TIMEOUT");
    }

    @Test
    @DisplayName("扫描后已被支付：跳过关单")
    void shouldSkipWhenNoLongerCreated() {
        PayOrderPo paid = createdOrder(1L);
        paid.setStatus("PAID");
        when(payOrderMapper.selectTimeoutCreated(any(), any(), anyInt()))
                .thenReturn(List.of(staleItem(1L)));
        when(payOrderMapper.selectById(1L)).thenReturn(paid);

        assertEquals(0, service.closeTimeoutOrders());

        verify(payOrderMapper, never()).closeIfCreated(any(), any());
        verify(payChannelGateway, never()).close(any());
    }
}

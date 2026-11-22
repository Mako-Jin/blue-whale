package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pay.config.PayNotifyProperties;
import com.bluewhale.pay.po.PayNotifyTaskPo;
import com.bluewhale.pay.mapper.PayNotifyTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PayNotifyServiceImpl} 通知任务单元测试：抢占、失败退避与 DEAD、
 * 手工重置、地址拆分。
 *
 * @author BlueWhale
 */
class PayNotifyServiceImplTest {

    /**
     * 初始化 MyBatis-Plus 实体元数据缓存。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayNotifyTaskPo.class);
    }

    private final PayNotifyTaskMapper taskMapper = mock(PayNotifyTaskMapper.class);

    private final DiscoveryClient discoveryClient = mock(DiscoveryClient.class);

    private final PayNotifyServiceImpl service = new PayNotifyServiceImpl(
            taskMapper, new ObjectMapper(), discoveryClient,
            new PayNotifyProperties(), Runnable::run, new SimpleMeterRegistry());

    private static PayNotifyTaskPo pendingTask() {
        PayNotifyTaskPo task = new PayNotifyTaskPo();
        task.setId(1L);
        task.setBizType("MALL_ORDER");
        task.setBizOrderNo("MO20261107001");
        task.setEvent("PAID");
        task.setNotifyUrl("whale-mall/mall/internal/order/paid");
        task.setPayload("{\"tenantId\":1,\"event\":\"PAID\"}");
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        return task;
    }

    @Test
    @DisplayName("deliverTask：任务不存在或非 PENDING 时按状态返回")
    void shouldHandleMissingAndNonPending() {
        when(taskMapper.selectById(99L)).thenReturn(null);
        assertFalse(service.deliverTask(99L));

        PayNotifyTaskPo success = pendingTask();
        success.setStatus("SUCCESS");
        when(taskMapper.selectById(1L)).thenReturn(success);
        assertTrue(service.deliverTask(1L));

        PayNotifyTaskPo dead = pendingTask();
        dead.setStatus("DEAD");
        when(taskMapper.selectById(2L)).thenReturn(dead);
        assertFalse(service.deliverTask(2L));
    }

    @Test
    @DisplayName("deliverTask：抢占失败（其他实例已领取）直接返回 false")
    void shouldSkipWhenClaimLost() {
        PayNotifyTaskPo task = pendingTask();
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.claimForDelivery(1L, 0)).thenReturn(0);

        assertFalse(service.deliverTask(1L));
        verify(taskMapper, never()).markSuccess(1L);
    }

    @Test
    @DisplayName("deliverTask：服务实例不可用记失败并安排退避")
    void shouldMarkFailureWhenInstanceMissing() {
        PayNotifyTaskPo task = pendingTask();
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.claimForDelivery(1L, 0)).thenReturn(1);
        when(discoveryClient.getInstances("whale-mall")).thenReturn(List.of());

        assertFalse(service.deliverTask(1L));

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskMapper).markFailure(eq(1L), eq(6), captor.capture(), any());
        assertTrue(captor.getValue().isAfter(LocalDateTime.now()));
        verify(taskMapper, never()).markSuccess(1L);
    }

    @Test
    @DisplayName("deliverTask：回调 HTTP 失败记失败原因")
    void shouldMarkFailureWhenHttpError() {
        PayNotifyTaskPo task = pendingTask();
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.claimForDelivery(1L, 0)).thenReturn(1);
        ServiceInstance instance = mock(ServiceInstance.class);
        when(instance.getUri()).thenReturn(java.net.URI.create("http://127.0.0.1:1"));
        when(discoveryClient.getInstances("whale-mall")).thenReturn(List.of(instance));

        assertFalse(service.deliverTask(1L));

        verify(taskMapper).markFailure(eq(1L), eq(6), any(LocalDateTime.class), any());
    }

    @Test
    @DisplayName("retryManually：DEAD 任务重置为 PENDING 并重试")
    void shouldResetDeadTaskOnManualRetry() {
        PayNotifyTaskPo dead = pendingTask();
        dead.setStatus("DEAD");
        dead.setRetryCount(6);
        // 第一次读到 DEAD 触发重置，第二次读到重置后的 PENDING 再投递
        when(taskMapper.selectById(1L)).thenReturn(dead, pendingTask());
        when(taskMapper.claimForDelivery(1L, 0)).thenReturn(1);
        when(discoveryClient.getInstances("whale-mall")).thenReturn(List.of());

        assertEquals("RETRY_PENDING", service.retryManually(1L).getStatus());

        verify(taskMapper).update(any(), any());
    }

    @Test
    @DisplayName("retryManually：已成功任务返回 SUCCESS 不再投递")
    void shouldSkipManualRetryWhenSuccess() {
        PayNotifyTaskPo success = pendingTask();
        success.setStatus("SUCCESS");
        when(taskMapper.selectById(1L)).thenReturn(success);

        assertEquals("SUCCESS", service.retryManually(1L).getStatus());
        verify(taskMapper, never()).update(any(), any());
        verify(taskMapper, never()).claimForDelivery(any(), anyInt());
    }

    @Test
    @DisplayName("retryManually：任务被其他实例领取时返回 IN_PROGRESS")
    void shouldReportInProgressWhenClaimLost() {
        PayNotifyTaskPo task = pendingTask();
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.claimForDelivery(1L, 0)).thenReturn(0);

        assertEquals("IN_PROGRESS", service.retryManually(1L).getStatus());
        verify(taskMapper, never()).markSuccess(1L);
    }

    @Test
    @DisplayName("retryManually：任务不存在抛 NOT_FOUND")
    void shouldThrowWhenManualRetryMissing() {
        when(taskMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.retryManually(99L));

        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("splitNotifyUrl：合法/非法地址拆分")
    void shouldSplitNotifyUrl() {
        String[] ok = PayNotifyServiceImpl.splitNotifyUrl("whale-mall/mall/internal/order/paid");
        assertEquals("whale-mall", ok[0]);
        assertEquals("/mall/internal/order/paid", ok[1]);

        assertNull(PayNotifyServiceImpl.splitNotifyUrl(null));
        assertNull(PayNotifyServiceImpl.splitNotifyUrl(""));
        assertNull(PayNotifyServiceImpl.splitNotifyUrl("no-slash"));
        assertNull(PayNotifyServiceImpl.splitNotifyUrl("svc/"));
    }

    @Test
    @DisplayName("retryBatch：按入参顺序去重，不存在的任务逐项返回 NOT_FOUND")
    void shouldRetryBatchDedupAndReportMissing() {
        when(taskMapper.selectById(1L)).thenReturn(null);
        when(taskMapper.selectById(2L)).thenReturn(null);

        var results = service.retryBatch(List.of(1L, 2L, 1L));

        assertEquals(2, results.size());
        assertEquals(1L, results.get(0).getId());
        assertEquals("NOT_FOUND", results.get(0).getStatus());
        assertEquals(2L, results.get(1).getId());
        assertEquals("NOT_FOUND", results.get(1).getStatus());
    }
}

package com.bluewhale.monitor.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.monitor.vo.ServiceInstanceVO;
import com.bluewhale.monitor.vo.ServiceSummaryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MonitorServiceImpl} 服务聚合、实例健康判定与未知服务分支单元测试。
 *
 * @author BlueWhale
 */
class MonitorServiceImplTest {

    private static final int NOT_FOUND_CODE = 404;

    private final DiscoveryClient discoveryClient = mock(DiscoveryClient.class);

    private final MonitorServiceImpl monitorService = new MonitorServiceImpl(discoveryClient);

    /**
     * 构造服务实例桩对象：主机、端口与 Nacos 健康元数据 {@code nacos.healthy}。
     *
     * @param host    主机地址
     * @param port    端口
     * @param healthy 健康标记
     * @return ServiceInstance 桩对象
     */
    private static ServiceInstance instance(String host, int port, boolean healthy) {
        ServiceInstance instance = mock(ServiceInstance.class);
        when(instance.getHost()).thenReturn(host);
        when(instance.getPort()).thenReturn(port);
        when(instance.getMetadata()).thenReturn(Map.of("nacos.healthy", Boolean.toString(healthy)));
        return instance;
    }

    @Test
    @DisplayName("listServices：聚合两个服务的实例数与健康实例数")
    void shouldAggregateServiceSummaries() {
        ServiceInstance authUp1 = instance("10.0.0.1", 8080, true);
        ServiceInstance authUp2 = instance("10.0.0.2", 8080, true);
        ServiceInstance jobUp = instance("10.0.0.3", 8083, true);
        ServiceInstance jobDown = instance("10.0.0.4", 8083, false);
        when(discoveryClient.getServices()).thenReturn(List.of("whale-auth", "whale-job"));
        when(discoveryClient.getInstances("whale-auth")).thenReturn(List.of(authUp1, authUp2));
        when(discoveryClient.getInstances("whale-job")).thenReturn(List.of(jobUp, jobDown));

        List<ServiceSummaryVO> summaries = monitorService.listServices();

        assertEquals(2, summaries.size());
        assertEquals("whale-auth", summaries.get(0).getServiceName());
        assertEquals(2, summaries.get(0).getInstanceCount());
        assertEquals(2, summaries.get(0).getHealthyInstanceCount());
        assertEquals("whale-job", summaries.get(1).getServiceName());
        assertEquals(2, summaries.get(1).getInstanceCount());
        assertEquals(1, summaries.get(1).getHealthyInstanceCount());
    }

    @Test
    @DisplayName("listInstances：返回实例主机端口与 UP/DOWN 状态")
    void shouldListInstancesWithHealthStatus() {
        ServiceInstance upInstance = instance("10.0.0.3", 8083, true);
        ServiceInstance downInstance = instance("10.0.0.4", 8083, false);
        when(discoveryClient.getServices()).thenReturn(List.of("whale-job"));
        when(discoveryClient.getInstances("whale-job")).thenReturn(List.of(upInstance, downInstance));

        List<ServiceInstanceVO> instances = monitorService.listInstances("whale-job");

        assertEquals(2, instances.size());
        ServiceInstanceVO up = instances.get(0);
        assertEquals("whale-job", up.getServiceName());
        assertEquals("10.0.0.3", up.getHost());
        assertEquals(8083, up.getPort());
        assertTrue(up.isHealthy());
        assertEquals("UP", up.getStatus());
        ServiceInstanceVO down = instances.get(1);
        assertEquals("10.0.0.4", down.getHost());
        assertFalse(down.isHealthy());
        assertEquals("DOWN", down.getStatus());
    }

    @Test
    @DisplayName("listInstances：未知服务名抛 NOT_FOUND 且不查询实例")
    void shouldThrowWhenServiceUnknown() {
        when(discoveryClient.getServices()).thenReturn(List.of("whale-auth", "whale-job"));

        BizException ex = assertThrows(BizException.class, () -> monitorService.listInstances("whale-unknown"));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(discoveryClient, never()).getInstances(eq("whale-unknown"));
    }
}

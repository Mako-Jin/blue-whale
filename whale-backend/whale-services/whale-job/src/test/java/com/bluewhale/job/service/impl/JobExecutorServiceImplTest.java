package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.entity.JobExecutor;
import com.bluewhale.job.mapper.JobExecutorMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobExecutorServiceImpl} 过期行清理与可派发地址解析测试。
 *
 * <p>清理阈值 = 心跳超时 × 倍数，倍数非法时至少按 1 倍处理；清理只做物理删除，不参与在线判定。
 * 可派发地址 = 注册表在线实例 ∩ 服务发现实例，交集为空或服务发现不可用时回退注册表。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class JobExecutorServiceImplTest {

    private static final String APP_NAME = "whale-crm";

    private static final String JOB_CODE = "crm.order.reconcile";

    private static final long TENANT_ID = 1L;

    @Mock
    private JobExecutorMapper jobExecutorMapper;

    @Mock
    private DiscoveryClient discoveryClient;

    /**
     * 注册实体元信息缓存：在线查询用 Lambda 条件构造器，纯单测无 MyBatis 环境需手动初始化。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, JobExecutor.class);
    }

    @Test
    @DisplayName("按心跳超时的倍数计算清理阈值并透传给 Mapper")
    void shouldPurgeWithMultipliedThreshold() {
        JobProperties properties = new JobProperties();
        properties.getExecutor().setHeartbeatTimeoutSeconds(90);
        properties.getExecutor().setStaleMultiplier(10);
        JobExecutorServiceImpl service = new JobExecutorServiceImpl(
                jobExecutorMapper, properties, unusedProvider());
        when(jobExecutorMapper.deleteStale(any())).thenReturn(3);

        int rows = service.purgeStale();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jobExecutorMapper).deleteStale(threshold.capture());
        assertEquals(3, rows);
        LocalDateTime expected = LocalDateTime.now().minusSeconds(900);
        assertTrue(Math.abs(Duration.between(threshold.getValue(), expected).getSeconds()) <= 2,
                "阈值应约为 900 秒前：" + threshold.getValue());
    }

    @Test
    @DisplayName("倍数配置为 0 或负数时按 1 倍处理，不会退化成「删除全部」")
    void shouldClampMultiplierToAtLeastOne() {
        JobProperties properties = new JobProperties();
        properties.getExecutor().setHeartbeatTimeoutSeconds(90);
        properties.getExecutor().setStaleMultiplier(0);
        JobExecutorServiceImpl service = new JobExecutorServiceImpl(
                jobExecutorMapper, properties, unusedProvider());
        when(jobExecutorMapper.deleteStale(any())).thenReturn(0);

        service.purgeStale();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jobExecutorMapper).deleteStale(threshold.capture());
        LocalDateTime expected = LocalDateTime.now().minusSeconds(90);
        assertTrue(Math.abs(Duration.between(threshold.getValue(), expected).getSeconds()) <= 2,
                "阈值应约为 90 秒前：" + threshold.getValue());
    }

    @Test
    @DisplayName("可派发地址只保留注册表与 Nacos 的交集：注册表残留的旧容器不应被派发")
    void shouldIntersectRegistryWithDiscovery() {
        JobExecutorServiceImpl service = new JobExecutorServiceImpl(
                jobExecutorMapper, new JobProperties(), discoveryProvider(discoveryClient));
        when(jobExecutorMapper.selectList(any()))
                .thenReturn(List.of(executor("bw-crm:9007"), executor("bw-crm-old:9007")));
        ServiceInstance liveInstance = instance("bw-crm", 9007);
        when(discoveryClient.getInstances(APP_NAME)).thenReturn(List.of(liveInstance));

        List<String> addresses = service.dispatchableAddresses(TENANT_ID, APP_NAME, JOB_CODE);

        assertEquals(List.of("bw-crm:9007"), addresses);
    }

    @Test
    @DisplayName("服务发现无实例时回退注册表，避免因服务发现异常让全部任务失败")
    void shouldFallbackToRegistryWhenDiscoveryEmpty() {
        JobExecutorServiceImpl service = new JobExecutorServiceImpl(
                jobExecutorMapper, new JobProperties(), discoveryProvider(discoveryClient));
        when(jobExecutorMapper.selectList(any()))
                .thenReturn(List.of(executor("bw-crm:9007")));
        when(discoveryClient.getInstances(eq(APP_NAME))).thenReturn(List.of());

        List<String> addresses = service.dispatchableAddresses(TENANT_ID, APP_NAME, JOB_CODE);

        assertEquals(List.of("bw-crm:9007"), addresses);
    }

    @Test
    @DisplayName("服务发现不可用（抛异常）时回退注册表")
    void shouldFallbackToRegistryWhenDiscoveryFails() {
        JobExecutorServiceImpl service = new JobExecutorServiceImpl(
                jobExecutorMapper, new JobProperties(), discoveryProvider(discoveryClient));
        when(jobExecutorMapper.selectList(any()))
                .thenReturn(List.of(executor("bw-crm:9007")));
        when(discoveryClient.getInstances(APP_NAME)).thenThrow(new IllegalStateException("nacos down"));

        List<String> addresses = service.dispatchableAddresses(TENANT_ID, APP_NAME, JOB_CODE);

        assertEquals(List.of("bw-crm:9007"), addresses);
    }

    /**
     * 构造一个在线执行器实体（心跳取当前时间，避免被判为过期）
     */
    private static JobExecutor executor(String address) {
        JobExecutor executor = new JobExecutor();
        executor.setTenantId(TENANT_ID);
        executor.setAppName(APP_NAME);
        executor.setAddress(address);
        executor.setJobCodes(JOB_CODE);
        executor.setStatus(1);
        executor.setLastBeat(LocalDateTime.now());
        return executor;
    }

    /**
     * 构造一个 Nacos 服务实例
     */
    private static ServiceInstance instance(String host, int port) {
        ServiceInstance instance = mock(ServiceInstance.class);
        when(instance.getHost()).thenReturn(host);
        when(instance.getPort()).thenReturn(port);
        return instance;
    }

    /**
     * 未 stub 的 {@link ObjectProvider}：清理测试不触碰服务发现，stub 了反而会被严格桩检查判为多余
     */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<DiscoveryClient> unusedProvider() {
        return (ObjectProvider<DiscoveryClient>) mock(ObjectProvider.class);
    }

    /**
     * 包装服务发现客户端为 {@link ObjectProvider}（模拟容器注入）
     */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<DiscoveryClient> discoveryProvider(DiscoveryClient client) {
        ObjectProvider<DiscoveryClient> provider = (ObjectProvider<DiscoveryClient>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }
}

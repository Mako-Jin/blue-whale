package com.bluewhale.monitor.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.monitor.service.MonitorService;
import com.bluewhale.monitor.vo.ServiceInstanceVO;
import com.bluewhale.monitor.vo.ServiceSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 服务监控查询服务实现
 *
 * <p>基于 Nacos Discovery 的 {@link DiscoveryClient}：{@code getServices()} 列出全部已注册服务，
 * {@code getInstances(serviceName)} 拉取实例；健康状态取自 Nacos 注入的实例元数据
 * {@code nacos.healthy}（值为 {@code "true"} / {@code "false"}，缺失时按健康处理）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    /** Nacos 注入的健康标记元数据键 */
    private static final String NACOS_HEALTHY_KEY = "nacos.healthy";

    /** 健康状态文案 */
    private static final String STATUS_UP = "UP";

    /** 不健康状态文案 */
    private static final String STATUS_DOWN = "DOWN";

    /** 不健康的元数据取值 */
    private static final String HEALTHY_FALSE = "false";

    private final DiscoveryClient discoveryClient;

    @Override
    public List<ServiceSummaryVO> listServices() {
        return discoveryClient.getServices().stream().map(this::summarize).toList();
    }

    @Override
    public List<ServiceInstanceVO> listInstances(String serviceName) {
        if (!discoveryClient.getServices().contains(serviceName)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return discoveryClient.getInstances(serviceName).stream()
                .map(instance -> toVo(serviceName, instance))
                .toList();
    }

    /**
     * 聚合单个服务的实例总数与健康实例数
     *
     * @param serviceName 服务名
     * @return 服务聚合概览视图对象
     */
    private ServiceSummaryVO summarize(String serviceName) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        long healthyCount = instances.stream().filter(MonitorServiceImpl::isHealthy).count();
        ServiceSummaryVO vo = new ServiceSummaryVO();
        vo.setServiceName(serviceName);
        vo.setInstanceCount(instances.size());
        vo.setHealthyInstanceCount((int) healthyCount);
        return vo;
    }

    /**
     * 服务实例转视图对象
     *
     * @param serviceName 服务名
     * @param instance    Nacos 服务实例
     * @return 服务实例视图对象
     */
    private static ServiceInstanceVO toVo(String serviceName, ServiceInstance instance) {
        boolean healthy = isHealthy(instance);
        ServiceInstanceVO vo = new ServiceInstanceVO();
        vo.setServiceName(serviceName);
        vo.setHost(instance.getHost());
        vo.setPort(instance.getPort());
        vo.setHealthy(healthy);
        vo.setStatus(healthy ? STATUS_UP : STATUS_DOWN);
        return vo;
    }

    /**
     * 判定实例是否健康：读取 Nacos 元数据 {@code nacos.healthy}，缺失时按健康处理
     *
     * @param instance Nacos 服务实例
     * @return {@code true} 健康
     */
    private static boolean isHealthy(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        if (metadata == null) {
            return true;
        }
        return !HEALTHY_FALSE.equalsIgnoreCase(metadata.get(NACOS_HEALTHY_KEY));
    }
}

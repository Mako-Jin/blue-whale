package com.bluewhale.agent.support.facts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时事实采集器：Nacos 服务实例列表 → {@code RUNTIME_SERVICE} 事实。
 *
 * <p>结果在内存缓存 60 秒，避免每次重建都打服务发现；采集失败时返回上一次快照。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeFactCollector implements FactCollector {

    /** 缓存有效期（毫秒） */
    private static final long CACHE_MILLIS = 60_000L;

    /** 运行时事实来源标识 */
    private static final String SOURCE = "runtime:nacos";

    private final DiscoveryClient discoveryClient;

    private volatile List<FactRecord> cache = List.of();

    private volatile long cachedAt;

    @Override
    public List<FactRecord> collect(Path repoRoot) {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MILLIS) {
            return cache;
        }
        try {
            List<FactRecord> facts = new ArrayList<>();
            for (String service : discoveryClient.getServices()) {
                if (service == null || service.isBlank()) {
                    continue;
                }
                facts.add(toFact(service, discoveryClient.getInstances(service)));
            }
            cache = facts;
            cachedAt = now;
            return facts;
        } catch (RuntimeException e) {
            log.warn("运行时服务列表采集失败，沿用上次快照: {}", e.getMessage());
            return cache;
        }
    }

    /**
     * 构造单个服务的运行时事实。
     *
     * @param service   服务名
     * @param instances 实例列表
     * @return 事实记录
     */
    private static FactRecord toFact(String service, List<ServiceInstance> instances) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ServiceInstance instance : instances) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("instanceId", instance.getInstanceId());
            item.put("host", instance.getHost());
            item.put("port", instance.getPort());
            item.put("uri", instance.getUri().toString());
            items.add(item);
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("instances", items);
        value.put("instanceCount", items.size());
        value.put("status", items.isEmpty() ? "DOWN" : "UP");
        value.put("capturedAt", Instant.now().toString());
        return new FactRecord("RUNTIME_SERVICE", service, value, SOURCE);
    }
}

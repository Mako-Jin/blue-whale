package com.bluewhale.gateway.route;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 网关路由业务属性覆盖提供者：维护权限中心下发的覆盖快照，供 {@code NacosRouteDefinitionRepository} 过滤路由。
 *
 * <p><b>刷新语义</b>：启动后延迟 {@code initial-delay-ms} 首拉，之后按 {@code refresh-interval-ms}
 * 周期拉取：</p>
 * <ul>
 *   <li>拉取成功：整体替换快照；内容与上一份等价时静默跳过（不重建、不发刷新事件）；</li>
 *   <li>内容发生变化：替换快照并发布 {@link RefreshRoutesEvent}，促使路由仓库立即重载过滤，
 *       变更在下一个刷新周期内生效，不重启；</li>
 *   <li>拉取失败（HTTP/超时/解析）：<b>保留上一份快照</b>，仅告警。</li>
 * </ul>
 *
 * <p>{@link AtomicBoolean} 防重入：慢请求叠加时跳过本次刷新。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class RouteOverrideProvider {

    private final RouteOverrideProperties properties;
    private final AuthGatewayRouteOverrideClient client;
    private final ApplicationEventPublisher eventPublisher;

    /** 覆盖规则快照（不可变列表，整体替换） */
    private final AtomicReference<List<RouteOverrideRule>> snapshot =
            new AtomicReference<>(List.of());

    /** 刷新防重入标记 */
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    /**
     * 构造路由覆盖提供者
     *
     * @param properties     路由覆盖配置
     * @param client         覆盖拉取客户端
     * @param eventPublisher 应用事件发布器（快照变更时发布路由刷新事件）
     */
    public RouteOverrideProvider(RouteOverrideProperties properties,
                                 AuthGatewayRouteOverrideClient client,
                                 ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.client = client;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 获取当前覆盖快照（按路由 ID 索引，供路由仓库 O(1) 查找）
     *
     * @return 路由 ID → 覆盖规则；无覆盖时为空映射
     */
    public Map<String, RouteOverrideRule> overrides() {
        return snapshot.get().stream()
                .collect(Collectors.toMap(RouteOverrideRule::routeId, Function.identity()));
    }

    /**
     * 覆盖层是否生效（对应配置 {@code whale.gateway.route-override.enabled}）
     *
     * @return 生效返回 {@code true}
     */
    public boolean isOverrideEnabled() {
        return properties.isEnabled();
    }

    /**
     * 定时刷新覆盖快照：首次延迟 {@code initial-delay-ms}，之后按 {@code refresh-interval-ms} 周期执行
     */
    @Scheduled(initialDelayString = "${whale.gateway.route-override.initial-delay-ms:5000}",
            fixedDelayString = "${whale.gateway.route-override.refresh-interval-ms:60000}")
    public void refresh() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            log.debug("上一次网关路由覆盖拉取尚未结束，跳过本次刷新");
            return;
        }
        client.fetch().doOnNext(this::replaceSnapshot)
                .doFinally(signal -> refreshing.set(false))
                .subscribe(rules -> { },
                        e -> log.warn("拉取网关路由覆盖失败，继续使用现有快照: reason={}", e.getMessage()));
    }

    /**
     * 原子替换快照：内容变化时发布路由刷新事件
     *
     * @param rules 远程下发的覆盖规则列表
     */
    private void replaceSnapshot(List<RouteOverrideRule> rules) {
        List<RouteOverrideRule> safe = rules == null ? List.of() : List.copyOf(rules);
        List<RouteOverrideRule> previous = snapshot.getAndSet(safe);
        if (previous.equals(safe)) {
            log.debug("网关路由覆盖未变化，保持当前快照: overrideCount={}", safe.size());
            return;
        }
        log.info("网关路由覆盖已刷新: {} 条，发布路由刷新事件", safe.size());
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
    }
}

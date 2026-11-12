package com.bluewhale.gateway.whitelist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网关白名单提供者：维护权限中心下发白名单的快照，供 {@code JwtAuthGlobalFilter} 只读匹配。
 *
 * <p><b>刷新语义</b>：应用启动后延迟 {@code initial-delay-ms} 首拉，之后按
 * {@code refresh-interval-ms} 周期拉取；</p>
 * <ul>
 *   <li>拉取成功且非空：整体替换快照；</li>
 *   <li>拉取成功但为空：清空快照（等价于「仅本地配置生效」，不放大放行范围）；</li>
 *   <li>拉取失败（HTTP/超时/解析）：<b>保留上一份快照</b>，仅告警，避免抖动期白名单被清空。</li>
 * </ul>
 *
 * <p>{@link AtomicBoolean} 防重入：慢请求叠加时跳过本次刷新，避免并发覆盖快照。
 * 匹配为内存遍历（条目约 15 条），不引入额外缓存。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class GatewayWhitelistProvider {

    private final GatewayWhitelistDynamicProperties properties;
    private final AuthGatewayWhitelistClient client;

    /** Ant 路径匹配器（线程安全，复用单例） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 远程白名单快照（不可变列表，整体替换） */
    private final AtomicReference<List<GatewayWhitelistRule>> snapshot = new AtomicReference<>(List.of());

    /** 刷新防重入标记 */
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    /**
     * 构造白名单提供者
     *
     * @param properties 动态白名单配置
     * @param client     白名单拉取客户端
     */
    public GatewayWhitelistProvider(GatewayWhitelistDynamicProperties properties,
                                    AuthGatewayWhitelistClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 判断请求是否命中远程白名单快照
     *
     * @param path   请求路径
     * @param method 请求方法（如 {@code GET}），可空
     * @return 命中任一规则返回 {@code true}
     */
    public boolean matches(String path, String method) {
        if (path == null) {
            return false;
        }
        for (GatewayWhitelistRule rule : snapshot.get()) {
            if (rule.matchesMethod(method) && pathMatcher.match(rule.pathPattern(), path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 定时刷新远程白名单：首次延迟 {@code initial-delay-ms}，之后按 {@code refresh-interval-ms} 周期执行。
     */
    @Scheduled(initialDelayString = "${whale.gateway.whitelist-dynamic.initial-delay-ms:5000}",
            fixedDelayString = "${whale.gateway.whitelist-dynamic.refresh-interval-ms:60000}")
    public void refresh() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            log.debug("上一次网关白名单拉取尚未结束，跳过本次刷新");
            return;
        }
        client.fetch()
                .doOnNext(this::replaceSnapshot)
                .doFinally(signal -> refreshing.set(false))
                .subscribe(rules -> { },
                        e -> log.warn("拉取网关白名单失败，继续使用现有快照与本地配置: reason={}", e.getMessage()));
    }

    /**
     * 原子替换快照，并按结果输出日志
     *
     * @param rules 远程下发的规则列表（可空）
     */
    private void replaceSnapshot(List<GatewayWhitelistRule> rules) {
        List<GatewayWhitelistRule> safe = rules == null ? List.of() : List.copyOf(rules);
        snapshot.set(safe);
        if (safe.isEmpty()) {
            log.warn("网关白名单下发结果为空，仅使用本地 white-list 兜底");
        } else {
            log.info("网关白名单已刷新: {} 条", safe.size());
        }
    }
}

package com.bluewhale.gateway.accesslist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 网关访问名单提供者：维护权限中心下发的白名单 / 黑名单快照，供 {@code JwtAuthGlobalFilter} 只读匹配。
 *
 * <p><b>优先级</b>：黑名单 &gt; 白名单。过滤器先调用 {@link #matchesBlacklist(String, String)}，
 * 命中即拒绝，不再判断白名单。</p>
 *
 * <p><b>刷新语义</b>（白名单、黑名单各自独立）：启动后延迟 {@code initial-delay-ms} 首拉，
 * 之后按 {@code refresh-interval-ms} 周期拉取：</p>
 * <ul>
 *   <li>拉取成功且非空：整体替换该名单快照；</li>
 *   <li>拉取成功但为空：清空该名单快照（白名单等价于「仅本地配置生效」，黑名单等价于「无封禁」）；</li>
 *   <li>拉取失败（HTTP/超时/解析）：<b>保留上一份快照</b>，仅告警，避免抖动期名单丢失。</li>
 * </ul>
 *
 * <p>两类名单各自用 {@link AtomicBoolean} 防重入：慢请求叠加时跳过本次刷新，避免并发覆盖快照。
 * 匹配为内存遍历（条目量级为十几条），不引入额外缓存。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class GatewayAccessListProvider {

    private final GatewayAccessListProperties properties;
    private final AuthGatewayAccessListClient client;

    /** Ant 路径匹配器（线程安全，复用单例） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 远程白名单快照（不可变列表，整体替换） */
    private final AtomicReference<List<GatewayAccessListRule>> whitelist =
            new AtomicReference<>(List.of());

    /** 远程黑名单快照（不可变列表，整体替换） */
    private final AtomicReference<List<GatewayAccessListRule>> blacklist =
            new AtomicReference<>(List.of());

    /** 白名单刷新防重入标记 */
    private final AtomicBoolean whitelistRefreshing = new AtomicBoolean(false);

    /** 黑名单刷新防重入标记 */
    private final AtomicBoolean blacklistRefreshing = new AtomicBoolean(false);

    /**
     * 构造访问名单提供者
     *
     * @param properties 访问名单配置
     * @param client     名单拉取客户端
     */
    public GatewayAccessListProvider(GatewayAccessListProperties properties,
                                     AuthGatewayAccessListClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 判断请求是否命中黑名单（优先级高于白名单）
     *
     * <p>先匹配 Nacos 应急黑名单（不限方法、不依赖 auth），再匹配远程黑名单快照。</p>
     *
     * @param path   请求路径
     * @param method 请求方法（如 {@code GET}），可空
     * @return 命中任一封禁规则返回 {@code true}
     */
    public boolean matchesBlacklist(String path, String method) {
        return matchesEmergencyBlacklist(path) || matches(blacklist.get(), path, method);
    }

    /**
     * 判断请求是否命中 Nacos 应急黑名单：路径模式不限方法，空白项忽略
     *
     * @param path 请求路径
     * @return 命中返回 {@code true}
     */
    private boolean matchesEmergencyBlacklist(String path) {
        if (path == null) {
            return false;
        }
        List<String> patterns = properties.getEmergencyBlacklist();
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isBlank() && pathMatcher.match(pattern.trim(), path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断请求是否命中远程白名单快照
     *
     * @param path   请求路径
     * @param method 请求方法（如 {@code GET}），可空
     * @return 命中任一放行规则返回 {@code true}
     */
    public boolean matchesWhitelist(String path, String method) {
        return matches(whitelist.get(), path, method);
    }

    /**
     * 在给定规则快照中匹配路径与方法
     *
     * @param rules  规则快照
     * @param path   请求路径
     * @param method 请求方法
     * @return 命中返回 {@code true}
     */
    private boolean matches(List<GatewayAccessListRule> rules, String path, String method) {
        if (path == null) {
            return false;
        }
        for (GatewayAccessListRule rule : rules) {
            if (rule.matchesMethod(method) && pathMatcher.match(rule.pathPattern(), path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 定时刷新远程白名单与黑名单：首次延迟 {@code initial-delay-ms}，之后按 {@code refresh-interval-ms} 周期执行。
     *
     * <p>两类名单独立拉取、独立降级：黑名单拉取失败不影响白名单刷新，反之亦然。</p>
     */
    @Scheduled(initialDelayString = "${whale.gateway.access-list.initial-delay-ms:5000}",
            fixedDelayString = "${whale.gateway.access-list.refresh-interval-ms:60000}")
    public void refresh() {
        if (!properties.isEnabled()) {
            return;
        }
        refreshList(client::fetchWhitelist, whitelist, whitelistRefreshing, "白名单", "仅使用本地 white-list 兜底");
        refreshList(client::fetchBlacklist, blacklist, blacklistRefreshing, "黑名单", "当前无封禁路径");
    }

    /**
     * 拉取单类名单并原子替换快照
     *
     * @param source       名单数据源（延迟创建，避免被防重入跳过时仍构造请求）
     * @param target       快照引用
     * @param flag         防重入标记
     * @param label        名单名称（日志用）
     * @param emptyMessage 名单为空时的补充说明（日志用）
     */
    private void refreshList(Supplier<Mono<List<GatewayAccessListRule>>> source,
                             AtomicReference<List<GatewayAccessListRule>> target,
                             AtomicBoolean flag, String label, String emptyMessage) {
        if (!flag.compareAndSet(false, true)) {
            log.debug("上一次网关{}拉取尚未结束，跳过本次刷新", label);
            return;
        }
        source.get().doOnNext(rules -> replaceSnapshot(target, rules, label, emptyMessage))
                .doFinally(signal -> flag.set(false))
                .subscribe(rules -> { },
                        e -> log.warn("拉取网关{}失败，继续使用现有快照与本地配置: reason={}", label, e.getMessage()));
    }

    /**
     * 原子替换快照，并按结果输出日志
     *
     * @param target       快照引用
     * @param rules        远程下发的规则列表（可空）
     * @param label        名单名称（日志用）
     * @param emptyMessage 名单为空时的补充说明（日志用）
     */
    private void replaceSnapshot(AtomicReference<List<GatewayAccessListRule>> target,
                                 List<GatewayAccessListRule> rules, String label, String emptyMessage) {
        List<GatewayAccessListRule> safe = rules == null ? List.of() : List.copyOf(rules);
        target.set(safe);
        if (safe.isEmpty()) {
            log.warn("网关{}下发结果为空，{}", label, emptyMessage);
        } else {
            log.info("网关{}已刷新: {} 条", label, safe.size());
        }
    }
}

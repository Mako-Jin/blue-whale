package com.bluewhale.gateway.accesslist;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关访问名单（白名单 / 黑名单）动态加载配置（{@code whale.gateway.access-list.*}）。
 *
 * <p>名单以权限中心（whale-service-auth）数据库为准：网关按 {@link #refreshIntervalMs}
 * 周期拉取两个只读内部接口，进程内各存一份快照：</p>
 * <ul>
 *   <li>白名单：与本地 {@code whale.gateway.white-list} 取并集后放行；</li>
 *   <li>黑名单：<b>优先于白名单</b>，命中即 403（即使同时命中白名单）。</li>
 * </ul>
 *
 * <p>远程不可用时保留上一份快照；白名单另有本地配置兜底，保证登录/刷新链路不因 auth 抖动全量 401。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.gateway.access-list")
public class GatewayAccessListProperties {

    /** 是否启用动态拉取；关闭后白名单仅用本地 {@code white-list}，黑名单不生效 */
    private boolean enabled = true;

    /** 白名单只读内部接口地址（{@code lb://} 由负载均衡 WebClient 解析） */
    private String whitelistUri = "lb://whale-service-auth/auth/v1/internal/gateway/whitelist";

    /** 黑名单只读内部接口地址 */
    private String blacklistUri = "lb://whale-service-auth/auth/v1/internal/gateway/blacklist";

    /** 刷新周期（毫秒） */
    private long refreshIntervalMs = 60_000L;

    /** 首次拉取延迟（毫秒） */
    private long initialDelayMs = 5_000L;

    /** 拉取超时（毫秒）：auth 抖动时快速失败，保留上一份快照 */
    private long timeoutMillis = 800L;

    /** 内网令牌（对应服务侧 {@code WHALE_INTERNAL_TOKEN}），为空时不发送令牌头 */
    private String internalToken = "";

    /** 内网令牌请求头名 */
    private String internalTokenHeader = "X-Internal-Token";
}

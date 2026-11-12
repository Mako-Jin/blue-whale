package com.bluewhale.gateway.whitelist;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关白名单动态加载配置（{@code whale.gateway.whitelist-dynamic.*}）。
 *
 * <p>白名单以权限中心（whale-service-auth）数据库为准：网关按
 * {@link #refreshIntervalMs} 周期拉取只读内部接口，与本地
 * {@code whale.gateway.white-list} 取并集后放行。远程不可用时仅本地配置生效，
 * 保证登录/刷新链路不因 auth 抖动而全量 401。</p>
 *
 * <p>配置项独立于 {@code white-list}，避免 List 与嵌套对象绑定的歧义。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.gateway.whitelist-dynamic")
public class GatewayWhitelistDynamicProperties {

    /** 是否启用动态白名单拉取；关闭后仅使用本地 {@code white-list} */
    private boolean enabled = true;

    /** 白名单只读内部接口地址（{@code lb://} 由负载均衡 WebClient 解析） */
    private String uri = "lb://whale-service-auth/auth/v1/internal/gateway/whitelist";

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

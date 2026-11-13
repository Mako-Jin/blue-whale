package com.bluewhale.gateway.route;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关路由业务属性覆盖动态加载配置（{@code whale.gateway.route-override.*}，GW-10 Phase 2）。
 *
 * <p>DB 不存路由定义（权威源为 Nacos，冷备为网关本地 YAML），只存业务属性覆盖
 * （enabled/生效窗口），表落权限中心（whale-service-auth）库。网关按 {@link #refreshIntervalMs}
 * 周期拉取只读内部接口的快照，在路由合并结果上过滤：停用与窗口外的路由从生效路由中移除，
 * 快照变更后发布 {@code RefreshRoutesEvent} 立即重载，不重启。</p>
 *
 * <p>远程不可用时保留上一份覆盖快照（与访问名单同范式），不引入 JDBC。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.gateway.route-override")
public class RouteOverrideProperties {

    /** 是否启用动态拉取；关闭后路由覆盖层不生效（Nacos/兜底路由全量放行） */
    private boolean enabled = true;

    /** 路由覆盖只读内部接口地址（{@code lb://} 由负载均衡 WebClient 解析） */
    private String uri = "lb://whale-service-auth/auth/v1/internal/gateway/route-overrides";

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

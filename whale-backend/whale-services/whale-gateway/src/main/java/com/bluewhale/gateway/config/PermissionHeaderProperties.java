package com.bluewhale.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关权限码头注入配置（AUTH-06，Wave 5.10）。
 *
 * <p>{@code whale.gateway.permission-header.*}：{@code enabled} 控制是否由
 * {@code PermissionHeaderGlobalFilter} 解析并注入权限码头；{@code codesHeader} 为注入的头名；
 * {@code cacheKeyPrefix}、{@code tenantIdHeader} 与 {@code nullSentinel} 与 whale-service-auth 侧
 * {@code CacheConsistencyTemplate} 的 Key 约定（{@code whale:{tenantId}:cache:{name}:{id}}）
 * 和空值哨兵保持一致，避免两侧口径漂移。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.gateway.permission-header")
public class PermissionHeaderProperties {

    /** 是否启用权限码头注入，默认开启；关闭后本过滤器直接放行不注入 */
    private boolean enabled = true;

    /** 权限码头名（与 whale-audit 侧 {@code whale.permission.header.codes-header} 默认值一致） */
    private String codesHeader = "X-Permission-Codes";

    /** 权限码缓存键前缀，最终键为 {@code {cacheKeyPrefix}:{tenantId}:cache:auth:user:perm:{userId}} */
    private String cacheKeyPrefix = "whale";

    /** 租户 ID 请求头（由 JwtAuthGlobalFilter 从 JWT claim 注入） */
    private String tenantIdHeader = "X-Tenant-Id";

    /** 空值哨兵串，与 whale-service-auth 侧 {@code whale.cache.null-sentinel} 默认值一致，命中视为未命中 */
    private String nullSentinel = "__NULL__";

    /**
     * 回落 {@code /auth/me} 的超时（毫秒），默认 800。
     *
     * <p>Redis 未命中且 auth 抖动时必须快速失败：无超时会让每个请求都挂在 auth 调用上，
     * 超时后按 fail-closed 注入空权限码头，由下游鉴权拒绝，避免拖垮网关请求处理。</p>
     */
    private long fallbackTimeoutMillis = 800L;
}

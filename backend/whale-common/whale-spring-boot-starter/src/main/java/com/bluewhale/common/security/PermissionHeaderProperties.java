package com.bluewhale.common.security;

import com.bluewhale.common.web.WebHeaders;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 权限码头判定配置（AUTH-06）。
 *
 * <p>{@code whale.permission.header.*}：{@code enabled} 控制是否装配 {@link RequirePermissionInterceptor}
 * （默认 {@code false}，仅显式开启的服务生效，避免在 whale-auth 内与本服务 {@code PermissionInterceptor}
 * 重复判定）；{@code codes-header} 为权限码头名（可改，默认与网关一致）。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.permission.header")
public class PermissionHeaderProperties {

    /** 是否启用权限码头判定拦截器（默认关闭，仅 whale-audit 等显式开启） */
    private boolean enabled = false;

    /** 权限码头名（默认与网关注入头一致） */
    private String codesHeader = WebHeaders.PERMISSION_CODES;
}

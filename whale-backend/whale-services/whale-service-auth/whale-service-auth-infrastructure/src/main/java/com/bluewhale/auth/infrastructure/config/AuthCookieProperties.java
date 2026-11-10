package com.bluewhale.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证 Cookie 配置属性（V-03：refresh 令牌迁移 HttpOnly Cookie）
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.auth.cookie")
public class AuthCookieProperties {

    /**
     * 是否为 refresh / CSRF Cookie 附加 {@code Secure} 标记。
     * <p>HTTPS 站点必须开启（生产通过环境变量 {@code WHALE_AUTH_COOKIE_SECURE=true}）；
     * dev 走 HTTP 联调默认关闭，否则浏览器丢弃 Cookie 导致刷新持续 401。</p>
     */
    private boolean secure = false;
}

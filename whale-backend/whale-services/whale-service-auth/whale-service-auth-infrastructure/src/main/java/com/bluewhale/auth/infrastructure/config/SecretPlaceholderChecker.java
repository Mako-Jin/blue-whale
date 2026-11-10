package com.bluewhale.auth.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 生产安全配置强制校验器（REQ-SEC-005）。
 * <p>非 dev 环境下校验两项配置，任一不安全即拒绝启动：</p>
 * <ol>
 *   <li>{@code whale.auth.jwt.secret} 仍为默认占位值——避免以弱密钥签发可在全平台通用的 JWT；</li>
 *   <li>{@code whale.auth.cookie.secure=false}——避免 refresh / CSRF Cookie 在 HTTP 明文通道传输
 *       而被中间人窃取（N-03）。</li>
 * </ol>
 * <p>dev 环境保持原行为以支持本地 HTTP 联调。</p>
 * <p>落地方式选用 {@link InitializingBean}：在 Bean 装配阶段（早于端口监听、早于任何请求）
 * 即校验，失败抛出 {@link IllegalStateException} 使应用上下文启动中止。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecretPlaceholderChecker implements InitializingBean {

    /** 允许使用占位密钥的开发环境 profile 名 */
    private static final String DEV_PROFILE = "dev";

    private final JwtProperties jwtProperties;

    private final AuthCookieProperties authCookieProperties;

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (isDevProfile()) {
            log.warn("当前为 dev 环境，允许使用默认 JWT 占位密钥与关闭 Cookie Secure；生产环境必须通过环境变量 JWT_SECRET / WHALE_AUTH_COOKIE_SECURE 覆盖");
            return;
        }
        if (JwtProperties.DEFAULT_PLACEHOLDER_SECRET.equals(jwtProperties.getSecret())) {
            throw new IllegalStateException("JWT 签名密钥仍为默认占位值，非 dev 环境拒绝启动。"
                    + "请通过环境变量 JWT_SECRET 设置至少 32 字节（HS256 要求 256 bit）的随机密钥。");
        }
        if (!authCookieProperties.isSecure()) {
            throw new IllegalStateException("认证 Cookie 的 Secure 标记未开启，非 dev 环境拒绝启动。"
                    + "请通过环境变量 WHALE_AUTH_COOKIE_SECURE=true 开启，避免 refresh / CSRF Cookie 经 HTTP 明文传输。");
        }
    }

    /**
     * 判断当前是否激活了 dev profile（大小写不敏感，支持 dev / DEV 等写法）
     */
    private boolean isDevProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (DEV_PROFILE.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}

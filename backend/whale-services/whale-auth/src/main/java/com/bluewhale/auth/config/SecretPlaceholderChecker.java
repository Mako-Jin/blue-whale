package com.bluewhale.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 生产密钥强制校验器（REQ-SEC-005）。
 * <p>非 dev 环境下，若 {@code whale.auth.jwt.secret} 仍为默认占位值，则直接拒绝启动，
 * 避免以弱密钥签发可在全平台通用的 JWT；dev 环境保持原行为以支持本地联调。</p>
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

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (isDevProfile()) {
            log.warn("当前为 dev 环境，允许使用默认 JWT 占位密钥；生产环境必须通过环境变量 JWT_SECRET 覆盖");
            return;
        }
        if (JwtProperties.DEFAULT_PLACEHOLDER_SECRET.equals(jwtProperties.getSecret())) {
            throw new IllegalStateException("JWT 签名密钥仍为默认占位值，非 dev 环境拒绝启动。"
                    + "请通过环境变量 JWT_SECRET 设置至少 32 字节（HS256 要求 256 bit）的随机密钥。");
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

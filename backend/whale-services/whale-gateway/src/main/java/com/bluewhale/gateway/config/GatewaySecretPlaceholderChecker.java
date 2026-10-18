package com.bluewhale.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 网关侧 JWT 占位密钥启动校验器（AUTH-01）。
 * <p>非 dev 环境下，若 {@code whale.gateway.jwt.secret} 仍为空或为默认占位值，则直接拒绝启动，
 * 避免网关以弱密钥校验可在全平台通用的 JWT；dev 环境保持原有行为以支持本地联调。</p>
 * <p>落地方式选用 {@link InitializingBean}：在 Bean 装配阶段（早于端口监听、早于任何请求）
 * 即完成校验，失败抛出 {@link IllegalStateException} 使应用上下文启动中止。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewaySecretPlaceholderChecker implements InitializingBean {

    /** 允许使用占位密钥的开发环境 profile 名 */
    private static final String DEV_PROFILE = "dev";

    /** 覆盖网关 JWT 签名密钥所需的环境变量名 */
    private static final String SECRET_ENV_NAME = "WHALE_GATEWAY_JWT_SECRET";

    /** 非 dev 环境命中空值或占位密钥时的拒绝原因，消息需能直接定位问题 */
    private static final String REJECT_MESSAGE = "网关 JWT 签名密钥为空或仍为默认占位值，非 dev 环境拒绝启动。"
            + "非 dev 环境必须覆盖 whale.gateway.jwt.secret（环境变量 " + SECRET_ENV_NAME + "），"
            + "请设置为至少 32 字节（HS256 要求 256 bit）的随机密钥。";

    private final WhaleGatewayProperties whaleGatewayProperties;

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (isDevProfile()) {
            log.warn("当前为 dev 环境，允许使用默认网关 JWT 占位密钥；生产环境必须通过环境变量 {} 覆盖", SECRET_ENV_NAME);
            return;
        }
        String secret = whaleGatewayProperties.getJwt().getSecret();
        if (secret == null || secret.isBlank() || WhaleGatewayProperties.Jwt.DEFAULT_PLACEHOLDER_SECRET.equals(secret)) {
            throw new IllegalStateException(REJECT_MESSAGE);
        }
    }

    /**
     * 判断当前是否激活了 dev profile（大小写不敏感，支持 dev / DEV 等写法）
     *
     * @return 激活的 profile 中含 dev 时返回 true，否则返回 false
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

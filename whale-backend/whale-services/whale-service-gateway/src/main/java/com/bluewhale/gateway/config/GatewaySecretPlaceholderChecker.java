package com.bluewhale.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

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

    /** HS256 要求的最小密钥字节数（256 bit），不足会在运行期抛 WeakKeyException */
    private static final int MIN_SECRET_BYTES = 32;

    /** 非 dev 环境命中弱密钥（长度不足）时的拒绝原因 */
    private static final String WEAK_SECRET_MESSAGE = "网关 JWT 签名密钥长度不足 32 字节（HS256 要求 256 bit），"
            + "非 dev 环境拒绝启动。请通过环境变量 " + SECRET_ENV_NAME + " 配置足够长的随机密钥。";

    /** 本地开发来源前缀，非 dev 环境出现即为 CORS 配置遗漏 */
    private static final String LOCAL_ORIGIN_PREFIX = "http://localhost";

    private final WhaleGatewayProperties whaleGatewayProperties;

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (isDevProfile()) {
            log.warn("当前为 dev 环境，允许使用默认网关 JWT 占位密钥；生产环境必须通过环境变量 {} 覆盖", SECRET_ENV_NAME);
            return;
        }
        String secret = whaleGatewayProperties.getJwt().getSecret();
        if (isJwksMode()) {
            // JWKS（RS256）模式下验签公钥来自 auth，共享密钥可以留空；存量 HS256 令牌需自行保证 secret 有效
            log.info("网关启用 JWKS 验签（RS256），共享密钥可留空；如仍需验签存量 HS256 令牌，请保持 secret 有效");
            warnIfCorsLooksLikeDev();
            warnIfTrustForwardedFor();
            return;
        }
        if (secret == null || secret.isBlank() || WhaleGatewayProperties.Jwt.DEFAULT_PLACEHOLDER_SECRET.equals(secret)) {
            throw new IllegalStateException(REJECT_MESSAGE);
        }
        // 长度校验前移到启动期：否则 hmacShaKeyFor 会在首个请求抛 WeakKeyException，表现为 401 难排查
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(WEAK_SECRET_MESSAGE);
        }
        warnIfCorsLooksLikeDev();
        warnIfTrustForwardedFor();
    }

    /**
     * 非 dev 环境提示 X-Forwarded-For 信任前提。
     *
     * <p>{@code trust-forwarded-for=true} 仅在网关位于可信代理（Nginx/Ingress/SLB）之后时才安全；
     * 网关直接暴露公网时，客户端可伪造 XFF 绕过基于 IP 的限流。状态无法从配置可靠推断
     * （{@code server.address} 未设置时绑定全部网卡），因此统一告警，由部署侧显式确认。</p>
     */
    private void warnIfTrustForwardedFor() {
        boolean trustForwardedFor = Boolean.parseBoolean(
                environment.getProperty("whale.gateway.rate-limit.trust-forwarded-for", "true"));
        if (trustForwardedFor) {
            log.warn("限流配置 trust-forwarded-for=true：请确认网关只能经可信代理访问；"
                    + "若网关直接暴露公网，客户端可伪造 X-Forwarded-For 绕过限流，"
                    + "请显式设置 WHALE_GATEWAY_RATE_LIMIT_TRUST_XFF=false");
        }
    }

    /**
     * 非 dev 环境下若 CORS 仍使用本地开发来源，仅告警不中断启动。
     *
     * <p>显式配置了来源说明有独立来源，但漏配 {@code WHALE_GATEWAY_CORS_ALLOWED_ORIGINS}
     * 时默认值 {@code http://localhost:*} 会让生产浏览器跨域调用被错误放行，因此在启动日志中提示。</p>
     */
    private void warnIfCorsLooksLikeDev() {
        WhaleGatewayProperties.Cors cors = whaleGatewayProperties.getCors();
        if (cors == null || cors.getAllowedOrigins() == null) {
            return;
        }
        for (String origin : cors.getAllowedOrigins()) {
            if (origin != null && origin.startsWith(LOCAL_ORIGIN_PREFIX)) {
                log.warn("非 dev 环境 CORS 允许来源仍包含本地开发地址 {}，请通过环境变量 "
                        + "WHALE_GATEWAY_CORS_ALLOWED_ORIGINS 覆盖为真实前端域名", origin);
                return;
            }
        }
    }

    /**
     * 判断是否启用 JWKS 验签
     *
     * @return 开关开启且 JWKS 地址非空时返回 {@code true}
     */
    private boolean isJwksMode() {
        WhaleGatewayProperties.Jwt jwt = whaleGatewayProperties.getJwt();
        return jwt.isJwksEnabled() && StringUtils.hasText(jwt.getJwksUri());
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

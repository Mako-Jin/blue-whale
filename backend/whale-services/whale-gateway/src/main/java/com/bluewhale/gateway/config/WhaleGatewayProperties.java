package com.bluewhale.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关配置属性
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.gateway")
public class WhaleGatewayProperties {

    /** JWT 配置 */
    private Jwt jwt = new Jwt();

    /** 不需要认证的白名单路径（支持 Ant 通配符） */
    private List<String> whiteList = new ArrayList<>();

    /**
     * JWT 相关配置
     *
     * @author BlueWhale
     */
    @Data
    public static class Jwt {

        /**
         * 默认占位密钥（仅限开发环境）。
         * <p>非 dev 环境若未通过环境变量 {@code WHALE_GATEWAY_JWT_SECRET} 覆盖，将被
         * {@link GatewaySecretPlaceholderChecker} 判定为不安全的配置并拒绝启动（AUTH-01）。</p>
         */
        public static final String DEFAULT_PLACEHOLDER_SECRET = "bluewhale-jwt-secret-2026-please-change-in-production";

        /** HS256 签名密钥，必须与 whale-auth 签发侧一致 */
        private String secret = DEFAULT_PLACEHOLDER_SECRET;
    }
}

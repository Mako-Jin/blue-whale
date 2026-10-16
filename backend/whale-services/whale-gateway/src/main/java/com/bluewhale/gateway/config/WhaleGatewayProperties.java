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

    @Data
    public static class Jwt {

        /** HS256 签名密钥，必须与 whale-auth 签发侧一致 */
        private String secret = "bluewhale-jwt-secret-2026-please-change-in-production";
    }
}

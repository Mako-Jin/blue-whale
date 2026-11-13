package com.bluewhale.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OAuth2.0 授权服务器配置属性（M1）。
 *
 * <p>仅暴露协议元数据所需的最小配置：{@code issuer} 必须与外部可见地址一致，
 * 否则客户端按 {@code /.well-known/oauth-authorization-server} 发现的端点会指向内网地址。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.auth.oauth2")
public class OAuth2ServerProperties {

    /** 授权服务器签发者标识（默认与本地直连地址一致，生产通过环境变量覆盖） */
    private String issuer = "http://localhost:8081";
}

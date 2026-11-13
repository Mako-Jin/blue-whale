package com.bluewhale.auth.infrastructure.social;

import com.bluewhale.auth.application.port.SocialWebConfig;
import com.bluewhale.auth.infrastructure.config.SocialLoginProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 三方登录 Web 配置适配器（权限中心扩展 M2）：把 Spring 配置属性桥接为应用层端口。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class SocialWebConfigAdapter implements SocialWebConfig {

    private final SocialLoginProperties properties;

    /**
     * 前端回调页基地址
     *
     * @return 基地址（末尾统一去斜杠）
     */
    @Override
    public String frontendBaseUrl() {
        String base = properties.getFrontendBaseUrl();
        return base != null && base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * state 有效期（秒）
     *
     * @return 有效期
     */
    @Override
    public long stateTtlSeconds() {
        return properties.getStateTtlSeconds();
    }

    /**
     * 一次性登录码有效期（秒）
     *
     * @return 有效期
     */
    @Override
    public long loginCodeTtlSeconds() {
        return properties.getLoginCodeTtlSeconds();
    }
}

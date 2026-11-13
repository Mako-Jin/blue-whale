package com.bluewhale.auth.application.port;

/**
 * 三方登录 Web 配置端口（权限中心扩展 M2）：应用层构建前端回跳 URL 与各存储 TTL 所需配置。
 *
 * <p>由基础设施适配器实现（{@code whale.auth.social} 配置段），应用层不感知 Spring 配置类型。</p>
 *
 * @author BlueWhale
 */
public interface SocialWebConfig {

    /**
     * 前端回调页基地址（三方登录/绑定完成后 302 回跳的落点）
     *
     * @return 基地址，如 {@code http://localhost:5173/social/callback}
     */
    String frontendBaseUrl();

    /**
     * state 有效期（秒）
     *
     * @return 有效期
     */
    long stateTtlSeconds();

    /**
     * 一次性登录码有效期（秒）
     *
     * @return 有效期
     */
    long loginCodeTtlSeconds();
}

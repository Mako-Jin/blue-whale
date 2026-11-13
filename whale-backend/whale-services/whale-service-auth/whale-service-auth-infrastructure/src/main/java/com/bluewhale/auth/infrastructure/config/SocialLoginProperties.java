package com.bluewhale.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 三方登录配置（权限中心扩展 M2，前缀 {@code whale.auth.social}）。
 *
 * <p>渠道密钥属敏感信息，生产环境经环境变量/Nacos 注入，配置文件仅保留结构骨架；
 * 渠道 {@code enabled=false} 时适配器拒绝服务（安全默认）。{@code callback-base-url}
 * 是服务侧接收渠道回调的对外基地址（含网关则为网关地址，服务直连则为服务地址）。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.auth.social")
public class SocialLoginProperties {

    /** 三方登录总开关 */
    private boolean enabled = false;
    /** state 有效期（秒） */
    private long stateTtlSeconds = 300;
    /** 一次性登录码有效期（秒） */
    private long loginCodeTtlSeconds = 60;
    /** 前端回调页基地址（三方登录完成后 302 回跳落点） */
    private String frontendBaseUrl = "http://localhost:5173/social/callback";
    /** 服务侧渠道回调基地址（拼接 /social/v1/{provider}/callback） */
    private String callbackBaseUrl = "http://localhost:8081";
    /** 渠道配置：key 与 SocialProvider.code() 一致（dingtalk/feishu/wecom） */
    private Map<String, ProviderCredential> providers = new LinkedHashMap<>();

    /**
     * 渠道凭据配置
     *
     * @author BlueWhale
     */
    @Data
    public static class ProviderCredential {

        /** 渠道是否启用 */
        private boolean enabled = false;
        /** 渠道应用 ID（钉钉 appKey / 飞书 app_id / 企业微信 corpid） */
        private String clientId;
        /** 渠道应用密钥（钉钉 appSecret / 飞书 app_secret / 企业微信 corpsecret） */
        private String clientSecret;
        /** 企业微信：应用 AgentId（扫码登录须与授权范围一致，可空） */
        private String agentId;
    }
}

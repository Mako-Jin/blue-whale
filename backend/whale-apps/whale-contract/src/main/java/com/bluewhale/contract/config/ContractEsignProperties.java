package com.bluewhale.contract.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 电子签章配置。
 *
 * <p>{@code p12-path} 为空时仅做视觉盖章 + 哈希存证（{@code algorithm=NONE}），
 * 符合 Wave 5.61 PoC 约束；配置 PKCS#12 后追加 CMS 数字签名。</p>
 *
 * <p>M3 新增：{@code provider} 决定默认签署渠道（local/esignbao/fadada/ssq），
 * 三方渠道通过 {@code providers.{name}} 配置接入参数；{@code callback-secret}
 * 为回调 HMAC 密钥，默认值仅供本地联调，生产必须用环境变量覆盖。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.contract.esign")
public class ContractEsignProperties {

    /** PKCS#12 证书路径（为空则不启用数字签名） */
    private String p12Path;

    /** PKCS#12 密码 */
    private String p12Password;

    /** 时间戳服务地址（预留，M3 启用） */
    private String tsaUrl;

    /** 默认签署渠道：local / esignbao / fadada / ssq */
    private String provider = "local";

    /** 回调时间戳允许偏移（秒），超出窗口一律拒绝 */
    private int callbackWindowSeconds = 300;

    /** 回调 HMAC 密钥（生产必须通过 CONTRACT_ESIGN_CALLBACK_SECRET 覆盖） */
    private String callbackSecret = "bluewhale-esign-dev-callback-secret";

    /** 三方渠道接入参数，按渠道名索引 */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /**
     * 是否启用数字签名。
     *
     * @return 配置了证书路径时返回 {@code true}
     */
    public boolean isDigitalSignatureEnabled() {
        return p12Path != null && !p12Path.isBlank();
    }

    /**
     * 三方渠道接入参数。
     */
    @Getter
    @Setter
    public static class ProviderConfig {

        /** 开放平台地址（预留，接入真实接口时使用） */
        private String baseUrl;

        /** 应用ID */
        private String appId;

        /** 应用密钥 */
        private String appSecret;

        /** 签署链接模板，支持 {providerTaskId} / {signTaskId} 占位 */
        private String signUrlTemplate;
    }
}

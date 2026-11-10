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

    /**
     * 默认回调 HMAC 密钥（仅限开发环境）。
     * <p>非 dev 环境若未通过环境变量 {@code CONTRACT_ESIGN_CALLBACK_SECRET} 覆盖，将被
     * {@link ContractEsignStartupChecker} 判定为不安全的配置并拒绝启动（REQ-SEC-010）。</p>
     */
    public static final String DEFAULT_CALLBACK_SECRET = "bluewhale-esign-dev-callback-secret";

    /** PKCS#12 证书路径（为空则不启用数字签名） */
    private String p12Path;

    /** PKCS#12 密码 */
    private String p12Password;

    /** RFC 3161 时间戳服务地址（为空则不请求时间戳） */
    private String tsaUrl;

    /** TSA Basic 认证用户名（可选） */
    private String tsaUsername;

    /** TSA Basic 认证口令（可选） */
    private String tsaPassword;

    /** TSA 请求超时（毫秒） */
    private int tsaTimeoutMillis = 5000;

    /**
     * 是否强制要求 RFC 3161 时间戳。
     * <p>开启后：未配置 {@code tsa-url} 拒绝启动；签署时 TSA 不可用直接失败，
     * 验签缺少时间戳判定为不通过（不允许静默降级）。</p>
     */
    private boolean tsaRequired = false;

    /** CA 证书链 PEM 文件（配置后校验 P12 证书链并把证书链写入 CMS） */
    private String caCertPath;

    /** CRL 文件（PEM 或 DER，配置后校验签名证书是否被吊销） */
    private String caCrlPath;

    /** 默认签署渠道：local / esignbao / fadada / ssq */
    private String provider = "local";

    /** 回调时间戳允许偏移（秒），超出窗口一律拒绝 */
    private int callbackWindowSeconds = 300;

    /** 回调 HMAC 密钥（生产必须通过 CONTRACT_ESIGN_CALLBACK_SECRET 覆盖） */
    private String callbackSecret = DEFAULT_CALLBACK_SECRET;

    /** 三方渠道接入参数，按渠道名索引 */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /** 回调补偿（乱序/丢失回调兜底） */
    private Reconcile reconcile = new Reconcile();

    /**
     * 是否启用数字签名。
     *
     * @return 配置了证书路径时返回 {@code true}
     */
    public boolean isDigitalSignatureEnabled() {
        return p12Path != null && !p12Path.isBlank();
    }

    /**
     * 是否启用 RFC 3161 时间戳。
     *
     * @return 配置了 {@code tsa-url} 时返回 {@code true}
     */
    public boolean isTimestampEnabled() {
        return tsaUrl != null && !tsaUrl.isBlank();
    }

    /**
     * 是否启用 CA 证书链校验与下发。
     *
     * @return 配置了 {@code ca-cert-path} 时返回 {@code true}
     */
    public boolean isChainValidationEnabled() {
        return caCertPath != null && !caCertPath.isBlank();
    }

    /**
     * 回调补偿配置：对长时间停留在 {@code SIGNING} 的非 local 任务轮询三方状态，
     * 发现终态时以内部签名回调走与真实回调一致的幂等链路。
     */
    @Getter
    @Setter
    public static class Reconcile {

        /** 是否启用补偿轮询 */
        private boolean enabled = true;

        /** 任务停留超过该秒数才参与轮询 */
        private int staleSeconds = 120;

        /** 单轮最多处理任务数 */
        private int batchSize = 50;
    }

    /**
     * 三方渠道接入参数。
     *
     * <p>接口路径用于适配供应商开放平台或其适配网关；默认值为 BlueWhale 电子签开放平台协议 v1，
     * 实际接入时按渠道文档覆盖。</p>
     */
    @Getter
    @Setter
    public static class ProviderConfig {

        /** 开放平台/适配网关地址 */
        private String baseUrl;

        /** 应用ID */
        private String appId;

        /** 应用密钥 */
        private String appSecret;

        /** 创建签署任务路径 */
        private String createPath = "/v1/sign-tasks";

        /** 查询签署任务路径，必须包含 {providerTaskId} 占位 */
        private String queryPath = "/v1/sign-tasks/{providerTaskId}";

        /** 下载签章件路径，必须包含 {providerTaskId} 占位 */
        private String downloadPath = "/v1/sign-tasks/{providerTaskId}/file";

        /** 三方回调本服务的外部地址（为空则不下发） */
        private String notifyUrl;

        /** 连接超时（毫秒） */
        private int connectTimeoutMillis = 3000;

        /** 读取超时（毫秒） */
        private int readTimeoutMillis = 10000;

        /** 签署链接兜底模板，支持 {providerTaskId} / {signTaskId} 占位 */
        private String signUrlTemplate;
    }
}

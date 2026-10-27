package com.bluewhale.contract.esign.provider;

import com.bluewhale.contract.config.ContractEsignProperties;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 三方电子签渠道公共实现：统一开放平台 HTTP 调用与 HMAC-SHA256 回调验签。
 *
 * <p>创建、查询、下载统一委托 {@link EsignOpenApiClient}，不再生成假任务号或返回固定
 * {@code SIGNING}。回调验签规则：{@code Base64(HMAC-SHA256(secret, timestamp + "\n"
 * + nonce + "\n" + rawBody))}，请求头 {@code X-Esign-Timestamp / X-Esign-Nonce /
 * X-Esign-Signature}；时间戳支持秒或毫秒，超出 {@code callback-window-seconds}
 * 窗口一律拒绝。真实平台若使用 RSA/SM2 验签，可覆盖 {@link #verifyCallback}。</p>
 *
 * @author BlueWhale
 */
public abstract class AbstractHmacEsignProvider implements EsignProvider {

    /** 时间戳请求头 */
    private static final String HEADER_TIMESTAMP = EsignCallbackSigner.HEADER_TIMESTAMP;

    /** 随机数请求头 */
    private static final String HEADER_NONCE = EsignCallbackSigner.HEADER_NONCE;

    /** 签名请求头 */
    private static final String HEADER_SIGNATURE = EsignCallbackSigner.HEADER_SIGNATURE;

    /** 秒级时间戳阈值（10 位） */
    private static final long SECOND_TIMESTAMP_MAX = 9_999_999_999L;

    /** 渠道名称（小写） */
    private final String providerName;

    /** 签章配置 */
    protected final ContractEsignProperties properties;

    /** 开放平台 HTTP 客户端 */
    private final EsignOpenApiClient openApiClient;

    /**
     * 构造渠道实现。
     *
     * @param providerName 渠道名称（小写）
     * @param properties   签章配置
     */
    protected AbstractHmacEsignProvider(String providerName, ContractEsignProperties properties) {
        this.providerName = providerName;
        this.properties = properties;
        Map<String, ContractEsignProperties.ProviderConfig> providers = properties.getProviders();
        this.openApiClient = new EsignOpenApiClient(providerName,
                providers == null ? null : providers.get(providerName));
    }

    @Override
    public String name() {
        return providerName;
    }

    /**
     * 调用三方开放平台创建签署任务。
     *
     * @param command 签署任务上下文
     * @return 三方任务号与签署链接
     */
    @Override
    public EsignTaskResult createTask(EsignTaskCommand command) {
        return openApiClient.createTask(command);
    }

    /**
     * 调用三方开放平台查询签署任务状态。
     *
     * @param providerTaskId 三方任务号
     * @return 三方任务状态
     */
    @Override
    public EsignTaskStatus queryTask(String providerTaskId) {
        return openApiClient.queryTask(providerTaskId);
    }

    /**
     * 调用三方开放平台下载签章件。
     *
     * @param providerTaskId 三方任务号
     * @return 签章件字节
     */
    @Override
    public byte[] downloadSignedFile(String providerTaskId) {
        return openApiClient.downloadSignedFile(providerTaskId);
    }

    @Override
    public boolean verifyCallback(String rawBody, Map<String, String> headers) {
        String timestamp = header(headers, HEADER_TIMESTAMP);
        String nonce = header(headers, HEADER_NONCE);
        String signature = header(headers, HEADER_SIGNATURE);
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            return false;
        }
        String secret = properties.getCallbackSecret();
        if (!StringUtils.hasText(secret)) {
            return false;
        }
        long timestampMillis;
        try {
            long rawTimestamp = Long.parseLong(timestamp.trim());
            timestampMillis = rawTimestamp <= SECOND_TIMESTAMP_MAX ? rawTimestamp * 1000L : rawTimestamp;
        } catch (NumberFormatException ex) {
            return false;
        }
        long windowMillis = Math.max(1, properties.getCallbackWindowSeconds()) * 1000L;
        if (Math.abs(System.currentTimeMillis() - timestampMillis) > windowMillis) {
            return false;
        }
        String content = timestamp.trim() + "\n" + nonce.trim() + "\n" + (rawBody == null ? "" : rawBody);
        String expected = hmacBase64(secret, content);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 大小写不敏感读取请求头。
     *
     * @param headers 请求头
     * @param name    头名称
     * @return 头值；缺失返回 {@code null}
     */
    private String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 计算 HMAC-SHA256 并 Base64 编码。
     *
     * @param secret  密钥
     * @param content 待签名内容
     * @return Base64 签名
     */
    private String hmacBase64(String secret, String content) {
        return EsignCallbackSigner.hmacBase64(secret, content);
    }
}

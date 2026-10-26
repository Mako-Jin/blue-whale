package com.bluewhale.contract.esign.provider;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.contract.config.ContractEsignProperties;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 三方电子签渠道骨架实现：统一 HMAC-SHA256 回调验签。
 *
 * <p>验签规则：{@code Base64(HMAC-SHA256(secret, timestamp + "\n" + nonce + "\n" + rawBody))}，
 * 请求头 {@code X-Esign-Timestamp / X-Esign-Nonce / X-Esign-Signature}；时间戳支持秒或毫秒，
 * 超出 {@code callback-window-seconds} 窗口一律拒绝。真实平台接入（e签宝 RSA/SM2、
 * 法大大事件签名等）在骨架基础上替换 {@link #verifyCallback} 与 HTTP 调用即可。</p>
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

    /**
     * 构造渠道骨架。
     *
     * @param providerName 渠道名称（小写）
     * @param properties   签章配置
     */
    protected AbstractHmacEsignProvider(String providerName, ContractEsignProperties properties) {
        this.providerName = providerName;
        this.properties = properties;
    }

    @Override
    public String name() {
        return providerName;
    }

    /**
     * 骨架创建任务：未接入真实开放平台时生成三方任务号，签署链接按配置模板生成。
     *
     * @param command 签署任务上下文
     * @return 三方任务号与签署链接
     */
    @Override
    public EsignTaskResult createTask(EsignTaskCommand command) {
        String providerTaskId = providerName.toUpperCase(Locale.ROOT) + "-"
                + UUID.randomUUID().toString().replace("-", "");
        ContractEsignProperties.ProviderConfig config = providerConfig();
        String signUrl = null;
        if (config != null && StringUtils.hasText(config.getSignUrlTemplate())) {
            signUrl = config.getSignUrlTemplate()
                    .replace("{providerTaskId}", providerTaskId)
                    .replace("{signTaskId}", String.valueOf(command.signTaskId()));
        }
        return new EsignTaskResult(providerTaskId, signUrl);
    }

    @Override
    public EsignTaskStatus queryTask(String providerTaskId) {
        return new EsignTaskStatus("SIGNING", null);
    }

    @Override
    public byte[] downloadSignedFile(String providerTaskId) {
        throw new BizException(ErrorCode.PARAM_ERROR,
                "三方签章件下载未接入，请在回调中携带 signedFileId 或 signedSha256");
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
     * 获取当前渠道接入参数。
     *
     * @return 渠道配置；未配置返回 {@code null}
     */
    protected ContractEsignProperties.ProviderConfig providerConfig() {
        Map<String, ContractEsignProperties.ProviderConfig> providers = properties.getProviders();
        return providers == null ? null : providers.get(providerName);
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

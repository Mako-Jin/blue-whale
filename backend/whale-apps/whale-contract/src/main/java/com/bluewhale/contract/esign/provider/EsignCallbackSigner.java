package com.bluewhale.contract.esign.provider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * 三方签章回调签名工具：统一 HMAC-SHA256 计算口径。
 *
 * <p>服务端验签（{@link AbstractHmacEsignProvider}）与回调补偿重放
 * （{@code SignTaskReconcileJob}）共用本工具，避免签名逻辑分叉。</p>
 *
 * @author BlueWhale
 */
public final class EsignCallbackSigner {

    /** 时间戳请求头 */
    public static final String HEADER_TIMESTAMP = "X-Esign-Timestamp";

    /** 随机数请求头 */
    public static final String HEADER_NONCE = "X-Esign-Nonce";

    /** 签名请求头 */
    public static final String HEADER_SIGNATURE = "X-Esign-Signature";

    /** HMAC 算法 */
    private static final String ALGORITHM = "HmacSHA256";

    /**
     * 工具类私有构造：禁止实例化。
     */
    private EsignCallbackSigner() {
    }

    /**
     * 按 {@code timestamp + "\n" + nonce + "\n" + body} 计算 HMAC-SHA256 并 Base64 编码。
     *
     * @param secret    回调密钥
     * @param timestamp 时间戳（秒或毫秒字符串）
     * @param nonce     随机数
     * @param body      原始请求体
     * @return Base64 签名
     */
    public static String sign(String secret, String timestamp, String nonce, String body) {
        String content = timestamp.trim() + "\n" + nonce.trim() + "\n" + (body == null ? "" : body);
        return hmacBase64(secret, content);
    }

    /**
     * 对已拼装内容计算 HMAC-SHA256 并 Base64 编码。
     *
     * @param secret  密钥
     * @param content 待签名内容
     * @return Base64 签名
     */
    public static String hmacBase64(String secret, String content) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", ex);
        }
    }
}

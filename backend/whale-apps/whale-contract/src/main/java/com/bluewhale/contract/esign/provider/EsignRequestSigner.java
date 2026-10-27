package com.bluewhale.contract.esign.provider;

import java.util.Locale;

/**
 * 三方电子签开放平台请求签名工具。
 *
 * <p>签名口径：{@code Base64(HMAC-SHA256(appSecret, method + "\n" + path + "\n"
 * + timestamp + "\n" + nonce + "\n" + body))}，请求头
 * {@code X-Esign-App-Id / X-Esign-Timestamp / X-Esign-Nonce / X-Esign-Signature}。
 * 与回调验签共用 HMAC-SHA256 基础实现，避免算法分叉。</p>
 *
 * @author BlueWhale
 */
public final class EsignRequestSigner {

    /** 应用ID请求头 */
    public static final String HEADER_APP_ID = "X-Esign-App-Id";

    /** 时间戳请求头 */
    public static final String HEADER_TIMESTAMP = "X-Esign-Timestamp";

    /** 随机数请求头 */
    public static final String HEADER_NONCE = "X-Esign-Nonce";

    /** 签名请求头 */
    public static final String HEADER_SIGNATURE = "X-Esign-Signature";

    /**
     * 工具类私有构造：禁止实例化。
     */
    private EsignRequestSigner() {
    }

    /**
     * 计算请求签名。
     *
     * @param secret    应用密钥
     * @param method    HTTP 方法
     * @param path      请求路径（含已替换的路径变量）
     * @param timestamp 秒级时间戳
     * @param nonce     随机数
     * @param body      请求体；GET 请求传空串
     * @return Base64 签名
     */
    public static String sign(String secret, String method, String path, String timestamp,
                              String nonce, String body) {
        String content = method.toUpperCase(Locale.ROOT) + "\n" + path + "\n" + timestamp + "\n"
                + nonce + "\n" + (body == null ? "" : body);
        return EsignCallbackSigner.hmacBase64(secret, content);
    }
}

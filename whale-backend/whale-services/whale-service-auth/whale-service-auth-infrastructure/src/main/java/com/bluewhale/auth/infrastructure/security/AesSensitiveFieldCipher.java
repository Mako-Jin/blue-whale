package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.application.port.SensitiveFieldCipher;
import com.bluewhale.security.crypto.AesCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 敏感字段加解密适配器：包装 whale-security 的 {@link AesCrypto}（AES-GCM）
 * 实现应用层 {@link SensitiveFieldCipher} 端口（权限中心扩展 M3）。
 *
 * <p>密钥从配置 {@code whale.auth.profile.crypto-key} 注入（生产走环境变量）；为空时
 * 回落内置默认开发密钥（仅本地默认，禁止生产使用）。配置值兼容两种格式：
 * Base64 编码的 16/24/32 字节密钥（按 {@link AesCrypto} 要求直接使用），或任意原始字符串
 * （按 SHA-256 派生为 32 字节密钥，保证确定性）。</p>
 *
 * @author BlueWhale
 */
@Component
public class AesSensitiveFieldCipher implements SensitiveFieldCipher {

    /** 内置默认开发密钥（仅本地默认；生产必须通过环境变量提供配置覆盖） */
    private static final String DEFAULT_DEV_KEY = "bluewhale-m3-profile-crypto-key!!";

    /** 最终参与加密的 Base64 密钥（构造期解析一次，运行期只读） */
    private final String base64Key;

    /**
     * 构造器：解析并缓存最终 Base64 密钥
     *
     * @param configuredKey 配置密钥（{@code whale.auth.profile.crypto-key}，可空）
     */
    public AesSensitiveFieldCipher(@Value("${whale.auth.profile.crypto-key:}") String configuredKey) {
        this.base64Key = resolveBase64Key(configuredKey);
    }

    /**
     * 加密明文（AES-GCM，返回 Base64 密文，随机 IV 前置）
     *
     * @param plaintext 明文（不可空）
     * @return Base64 密文
     */
    @Override
    public String encrypt(String plaintext) {
        return AesCrypto.encrypt(plaintext, base64Key);
    }

    /**
     * 解密密文（Base64）
     *
     * @param ciphertext Base64 密文（不可空）
     * @return 明文
     */
    @Override
    public String decrypt(String ciphertext) {
        return AesCrypto.decrypt(ciphertext, base64Key);
    }

    /**
     * 解析最终 Base64 密钥：配置为空回落默认开发密钥；
     * 值本身是合法 Base64 且解码后长度为 16/24/32 字节时直接使用，否则按 SHA-256 派生
     *
     * @param configuredKey 配置密钥（可空）
     * @return Base64 编码的 AES 密钥
     */
    private static String resolveBase64Key(String configuredKey) {
        String raw = StringUtils.hasText(configuredKey) ? configuredKey : DEFAULT_DEV_KEY;
        try {
            byte[] decoded = Base64.getDecoder().decode(raw);
            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return raw;
            }
        } catch (IllegalArgumentException ex) {
            // 非法 Base64：按原始字符串派生
        }
        return deriveBase64Key(raw);
    }

    /**
     * 由原始字符串派生 32 字节密钥（SHA-256，确定性映射，保证重启后可解密历史密文）
     *
     * @param raw 原始密钥字符串
     * @return Base64 编码的 32 字节密钥
     */
    private static String deriveBase64Key(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", ex);
        }
    }
}

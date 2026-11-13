package com.bluewhale.auth.application.port;

/**
 * 敏感字段加解密端口（权限中心扩展 M3）。
 *
 * <p>为档案域的证件号、密保答案提供对称加密（AES-GCM）能力；密钥由基础设施适配器
 * 从配置 {@code whale.auth.profile.crypto-key} 注入（环境变量化，禁止硬编码）。
 * 领域不变量「敏感字段必须密文落库」由调用方先加密再进入聚合保证。</p>
 *
 * @author BlueWhale
 */
public interface SensitiveFieldCipher {

    /**
     * 加密明文（AES-GCM，返回 Base64 密文，随机 IV 前置）
     *
     * @param plaintext 明文（不可空）
     * @return Base64 密文
     */
    String encrypt(String plaintext);

    /**
     * 解密密文（Base64）
     *
     * @param ciphertext Base64 密文（不可空）
     * @return 明文
     */
    String decrypt(String ciphertext);
}

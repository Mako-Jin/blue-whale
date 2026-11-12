package com.bluewhale.auth.application.port;

/**
 * 客户端密钥哈希端口（权限中心扩展 M0）：应用层不依赖 Spring Security，
 * 由基础设施层以 BCrypt 实现。
 *
 * @author BlueWhale
 */
public interface ClientSecretVerifier {

    /**
     * 计算客户端密钥哈希
     *
     * @param rawSecret 密钥明文
     * @return 哈希值（落库口径）
     */
    String hash(String rawSecret);

    /**
     * 校验密钥明文是否匹配哈希
     *
     * @param rawSecret 密钥明文
     * @param hashedSecret 已落库哈希
     * @return 匹配返回 {@code true}
     */
    boolean matches(String rawSecret, String hashedSecret);
}

package com.bluewhale.auth.application.port;

/**
 * 口令加密端口：由基础设施层基于具体加密算法实现，应用层只负责「明文转密文」与「比对」。
 *
 * @author BlueWhale
 */
public interface PasswordHasher {

    /**
     * 加密明文口令
     *
     * @param rawPassword 明文口令
     * @return 密文口令
     */
    String encode(String rawPassword);

    /**
     * 比对明文口令与密文口令
     *
     * @param rawPassword 明文口令
     * @param encodedPassword 已落库的密文口令
     * @return 匹配返回 true；任一参数为空时返回 false
     */
    boolean matches(String rawPassword, String encodedPassword);

    /**
     * 判断密文是否需要用当前强度重新编码（REQ-SEC-021：登录时自动升级旧 hash）
     *
     * @param encodedPassword 已落库的密文口令
     * @return 需要升级返回 true
     */
    boolean upgradeEncoding(String encodedPassword);
}

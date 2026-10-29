package com.bluewhale.auth.application.user.port;

/**
 * 口令加密端口：由基础设施层基于具体加密算法实现，应用层只负责「明文转密文」。
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
}

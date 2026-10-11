package com.bluewhale.sts.auth.common.codec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 摘要工具。
 *
 * <p>目前只有一个用途：把刷新令牌原文摘要成 {@code tokenHash} 落库。
 * 令牌本身是随机串而非口令，无需加盐或慢哈希 —— 它没有"低熵可被字典攻击"的问题，
 * 需要防的只是"库被读走后能直接拿去用"，一次 SHA-256 就够了。
 * 用加盐慢哈希反而会让刷新接口（每次都要比对）变慢，得不偿失。
 */
public final class Digests {

    private Digests() {
    }

    public static String sha256Base64(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 是 JDK 必备算法，走到这里说明运行环境异常
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

}

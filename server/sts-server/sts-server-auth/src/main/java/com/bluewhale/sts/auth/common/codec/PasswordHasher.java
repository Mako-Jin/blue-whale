package com.bluewhale.sts.auth.common.codec;

import com.bluewhale.sts.auth.common.AuthProperties;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 口令哈希器 —— PBKDF2-HMAC-SHA256。
 *
 * <p><b>为什么是 PBKDF2 而不是 Argon2 / BCrypt：</b>
 * <ul>
 *   <li>PBKDF2 是 JDK 内置算法（{@code SecretKeyFactory}），**零依赖**。
 *       Argon2 需要 password4j，BCrypt 需要 spring-security-crypto ——
 *       参照实现 {@code SystemTerminator} 虽然声明了 password4j，
 *       却全仓没有一处真正使用它，正是"声明了却不落地"的典型；</li>
 *   <li>PBKDF2 的抗暴力破解能力弱于 Argon2（不抗 GPU/ASIC），
 *       但优于"加盐一次 SHA"这类常见错误做法，对内部平台是够用的起点。
 *       若将来要换 Argon2，**存量无需迁移**：算法标识与参数随哈希存储。</li>
 * </ul>
 *
 * <p><b>存储格式</b>：{@code PBKDF2-SHA256$<迭代次数>$<盐Base64>$<摘要Base64>}。
 * 把参数写进哈希串而不是只写进配置，是为了让"用哪套参数校验"由**当时的存储值**决定 ——
 * 否则一旦调大迭代次数，所有存量口令立刻全部校验失败。
 */
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String ID_PREFIX = "PBKDF2-SHA256";
    private static final String SEPARATOR = "$";

    private final int iterations;
    private final int keyLengthBits;
    private final int saltLengthBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordHasher(AuthProperties properties) {
        this.iterations = properties.getPassword().getIterations();
        this.keyLengthBits = properties.getPassword().getKeyLengthBits();
        this.saltLengthBytes = properties.getPassword().getSaltLengthBytes();
    }

    /** 生成哈希串，可直接落库 */
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("口令不能为空");
        }
        byte[] salt = new byte[saltLengthBytes];
        secureRandom.nextBytes(salt);
        byte[] derived = derive(rawPassword.toCharArray(), salt, iterations, keyLengthBits);
        return ID_PREFIX + SEPARATOR + iterations + SEPARATOR
                + base64(salt) + SEPARATOR + base64(derived);
    }

    /**
     * 校验口令。
     *
     * <p>用 {@link MessageDigest#isEqual} 做恒定时间比较：普通数组比较会在
     * 第一个不同的字节处提前返回，比较耗时因此与"猜对了几位"相关，
     * 是可被远程利用的时间旁路。
     */
    public boolean matches(String rawPassword, String encoded) {
        if (rawPassword == null || encoded == null) {
            return false;
        }
        String[] parts = encoded.split("\\" + SEPARATOR);
        if (parts.length != 4 || !ID_PREFIX.equals(parts[0])) {
            // 不认识的格式一律判失败，而不是抛异常 —— 避免把"库里有脏数据"变成 500
            return false;
        }
        try {
            int storedIterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(rawPassword.toCharArray(), salt, storedIterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** 存储的哈希是否使用了低于当前配置的参数（登录成功后调用，决定是否重算） */
    public boolean needsRehash(String encoded) {
        if (encoded == null) {
            return false;
        }
        String[] parts = encoded.split("\\" + SEPARATOR);
        if (parts.length != 4 || !ID_PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            return Integer.parseInt(parts[1]) < iterations;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public String algorithmId() {
        return ID_PREFIX;
    }

    private byte[] derive(char[] password, byte[] salt, int iterationCount, int keyLength) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterationCount, keyLength);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("口令哈希失败", ex);
        } finally {
            spec.clearPassword();
        }
    }

    private static String base64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

}

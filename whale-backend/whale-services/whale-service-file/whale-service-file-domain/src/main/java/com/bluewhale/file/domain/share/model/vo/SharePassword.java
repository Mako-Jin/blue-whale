package com.bluewhale.file.domain.share.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 分享密码哈希工具：PBKDF2-HMAC-SHA256，按分享独立加盐。
 *
 * <p>存储格式 {@code pbkdf2$iterations$base64(salt)$base64(hash)}，迭代次数随哈希一起保存，
 * 便于后续调高成本而不影响存量校验。校验使用 {@link MessageDigest#isEqual(byte[], byte[])}
 * 做常量时间比较，避免按字节短路导致的时序侧信道。</p>
 *
 * @author BlueWhale
 */
public final class SharePassword {

    /** 密码最短长度 */
    public static final int MIN_LENGTH = 4;

    /** 密码最长长度 */
    public static final int MAX_LENGTH = 64;

    /** 哈希算法标识前缀 */
    private static final String PREFIX = "pbkdf2";

    /** 哈希字段分隔符 */
    private static final String FIELD_SEPARATOR = "$";

    /** 哈希字段分隔符的正则形式（拆分时使用） */
    private static final String FIELD_SEPARATOR_REGEX = "\\$";

    /** 密钥派生算法 */
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** 迭代次数：兼顾校验耗时与暴力破解成本 */
    private static final int ITERATIONS = 120_000;

    /** 盐字节数 */
    private static final int SALT_BYTES = 16;

    /** 派生密钥位数 */
    private static final int KEY_BITS = 256;

    /** 盐共享随机源 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Base64 编码器：URL 无填充，避免哈希串出现特殊字符 */
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** Base64 解码器 */
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private SharePassword() {
    }

    /**
     * 校验明文密码是否符合长度要求
     *
     * @param raw 明文密码
     * @throws DomainException 为空或长度不在 {@value #MIN_LENGTH}~{@value #MAX_LENGTH} 时抛出 {@code PARAM_ERROR}
     */
    public static void validate(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "分享密码不能为空");
        }
        if (raw.length() < MIN_LENGTH || raw.length() > MAX_LENGTH) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    "分享密码长度必须为 " + MIN_LENGTH + "~" + MAX_LENGTH + " 个字符");
        }
    }

    /**
     * 计算密码哈希
     *
     * @param raw 明文密码（调用方需先通过 {@link #validate(String)}）
     * @return 可直接落库的哈希串
     * @throws DomainException 密码不合法或本机缺少算法时抛出
     */
    public static String hash(String raw) {
        validate(raw);
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(raw, salt, ITERATIONS);
        return PREFIX + FIELD_SEPARATOR + ITERATIONS + FIELD_SEPARATOR
                + ENCODER.encodeToString(salt) + FIELD_SEPARATOR + ENCODER.encodeToString(derived);
    }

    /**
     * 校验明文密码是否匹配已存储的哈希
     *
     * @param raw 明文密码，可为 {@code null}（不匹配）
     * @param stored 已存储的哈希串
     * @return 匹配时返回 {@code true}
     * @throws DomainException 存储的哈希格式非法时抛出 {@code SYSTEM_ERROR}
     */
    public static boolean matches(String raw, String stored) {
        if (raw == null || raw.isEmpty() || stored == null || stored.isBlank()) {
            return false;
        }
        String[] parts = stored.split(FIELD_SEPARATOR_REGEX);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            throw new DomainException(ErrorCode.SYSTEM_ERROR, "分享密码哈希格式非法");
        }
        int iterations;
        byte[] salt;
        byte[] expected;
        try {
            iterations = Integer.parseInt(parts[1]);
            salt = DECODER.decode(parts[2]);
            expected = DECODER.decode(parts[3]);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.SYSTEM_ERROR, "分享密码哈希格式非法");
        }
        if (iterations <= 0 || salt.length == 0 || expected.length == 0) {
            throw new DomainException(ErrorCode.SYSTEM_ERROR, "分享密码哈希格式非法");
        }
        return MessageDigest.isEqual(expected, derive(raw, salt, iterations));
    }

    /**
     * 按 PBKDF2 派生密钥
     *
     * @param raw 明文密码
     * @param salt 盐
     * @param iterations 迭代次数
     * @return 派生密钥
     * @throws DomainException 算法不可用时抛出 {@code SYSTEM_ERROR}
     */
    private static byte[] derive(String raw, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new DomainException(ErrorCode.SYSTEM_ERROR, "分享密码哈希计算失败");
        } finally {
            spec.clearPassword();
        }
    }
}

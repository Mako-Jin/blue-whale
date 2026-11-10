package com.bluewhale.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES 加密工具
 * <p>采用 AEAD 模式 {@code AES/GCM/NoPadding}（12 字节随机 IV + 128 位认证标签），
 * 密文布局为 {@code IV || CipherText}，对外 Base64 编码。</p>
 * <p><b>禁止</b>使用 ECB 或固定 IV；每次加密均生成新 IV，相同明文密文不同。</p>
 *
 * @author BlueWhale
 */
public final class AesCrypto {

    /** 加密算法与模式 */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** 算法名 */
    private static final String ALGORITHM = "AES";

    /** GCM 推荐 IV 长度（字节） */
    private static final int IV_LENGTH = 12;

    /** GCM 认证标签长度（位） */
    private static final int TAG_LENGTH_BITS = 128;

    /** AES-128 密钥长度 */
    private static final int KEY_LENGTH_128 = 16;

    /** AES-192 密钥长度 */
    private static final int KEY_LENGTH_192 = 24;

    /** AES-256 密钥长度 */
    private static final int KEY_LENGTH_256 = 32;

    /** 生成密钥时的默认长度（256 位） */
    private static final int DEFAULT_KEY_LENGTH = KEY_LENGTH_256;

    /** 随机源 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 私有构造器，禁止实例化
     */
    private AesCrypto() {
    }

    /**
     * 生成随机 AES 密钥（256 位，Base64）
     *
     * @return Base64 密钥
     */
    public static String generateKey() {
        byte[] key = new byte[DEFAULT_KEY_LENGTH];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * 加密文本
     *
     * @param plaintext    明文（UTF-8）
     * @param base64Key    Base64 编码的密钥
     * @return Base64 编码的密文（IV 前置）
     */
    public static String encrypt(String plaintext, String base64Key) {
        if (plaintext == null) {
            throw new IllegalArgumentException("明文不能为空");
        }
        return Base64.getEncoder().encodeToString(
                encrypt(plaintext.getBytes(StandardCharsets.UTF_8), decodeKey(base64Key)));
    }

    /**
     * 解密文本
     *
     * @param ciphertext Base64 编码的密文（IV 前置）
     * @param base64Key  Base64 编码的密钥
     * @return 明文
     */
    public static String decrypt(String ciphertext, String base64Key) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("密文不能为空");
        }
        byte[] plain = decrypt(Base64.getDecoder().decode(ciphertext), decodeKey(base64Key));
        return new String(plain, StandardCharsets.UTF_8);
    }

    /**
     * 加密字节数组
     *
     * @param plaintext 明文
     * @param key       密钥（16/24/32 字节）
     * @return 密文（IV 前置）
     */
    public static byte[] encrypt(byte[] plaintext, byte[] key) {
        if (plaintext == null) {
            throw new IllegalArgumentException("明文不能为空");
        }
        validateKey(key);
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return result;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AES 加密失败", ex);
        }
    }

    /**
     * 解密字节数组
     *
     * @param ciphertext 密文（IV 前置）
     * @param key        密钥（16/24/32 字节）
     * @return 明文
     */
    public static byte[] decrypt(byte[] ciphertext, byte[] key) {
        if (ciphertext == null || ciphertext.length <= IV_LENGTH) {
            throw new IllegalArgumentException("密文长度非法");
        }
        validateKey(key);
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext, IV_LENGTH, ciphertext.length - IV_LENGTH);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AES 解密失败（密钥错误或密文被篡改）", ex);
        }
    }

    /**
     * 解码 Base64 密钥
     *
     * @param base64Key Base64 密钥
     * @return 原始密钥字节
     */
    private static byte[] decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("密钥不能为空");
        }
        return Base64.getDecoder().decode(base64Key);
    }

    /**
     * 校验密钥长度
     *
     * @param key 密钥
     */
    private static void validateKey(byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException("密钥不能为空");
        }
        boolean valid = key.length == KEY_LENGTH_128 || key.length == KEY_LENGTH_192 || key.length == KEY_LENGTH_256;
        if (!valid) {
            throw new IllegalArgumentException("AES 密钥长度必须为 16/24/32 字节");
        }
    }
}

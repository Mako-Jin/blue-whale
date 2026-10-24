package com.bluewhale.security.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Sm4Crypto} 单元测试
 *
 * @author BlueWhale
 */
class Sm4CryptoTest {

    /** GB/T 32907-2016 标准测试向量密钥 */
    private static final String STANDARD_KEY_HEX = "0123456789abcdeffedcba9876543210";

    /** GB/T 32907-2016 标准测试向量明文 */
    private static final String STANDARD_PLAIN_HEX = "0123456789abcdeffedcba9876543210";

    /** GB/T 32907-2016 单分组加密期望密文 */
    private static final String STANDARD_CIPHER_HEX = "681edf34d206965e86b3e94f536e4246";

    /** 文本用例使用的随机密钥（32 位十六进制） */
    private static final String SAMPLE_KEY_HEX = "0123456789abcdeffedcba9876543210";

    /**
     * 标准向量校验单分组加密
     */
    @Test
    @DisplayName("标准向量：单分组加密结果与 GB/T 32907 一致")
    void shouldMatchStandardVector() {
        HexFormat hex = HexFormat.of();
        byte[] encrypted = Sm4Crypto.encryptBlock(
                hex.parseHex(STANDARD_KEY_HEX), hex.parseHex(STANDARD_PLAIN_HEX));
        assertEquals(STANDARD_CIPHER_HEX, hex.formatHex(encrypted));
    }

    /**
     * 文本加解密往返
     */
    @Test
    @DisplayName("加密/解密：文本往返一致")
    void shouldRoundTripText() {
        String plaintext = "蓝鲸平台国密数据 2026";
        String ciphertext = Sm4Crypto.encrypt(plaintext, SAMPLE_KEY_HEX);
        assertNotEquals(plaintext, ciphertext);
        assertEquals(plaintext, Sm4Crypto.decrypt(ciphertext, SAMPLE_KEY_HEX));
    }

    /**
     * 随机 IV：相同明文两次密文不同
     */
    @Test
    @DisplayName("随机 IV：相同明文产生不同密文且均可解密")
    void shouldUseRandomIv() {
        String first = Sm4Crypto.encrypt("same", SAMPLE_KEY_HEX);
        String second = Sm4Crypto.encrypt("same", SAMPLE_KEY_HEX);
        assertNotEquals(first, second);
        assertEquals("same", Sm4Crypto.decrypt(first, SAMPLE_KEY_HEX));
        assertEquals("same", Sm4Crypto.decrypt(second, SAMPLE_KEY_HEX));
    }

    /**
     * 错误密钥解密失败
     */
    @Test
    @DisplayName("解密：错误密钥抛出异常")
    void shouldFailWithWrongKey() {
        String ciphertext = Sm4Crypto.encrypt("secret", SAMPLE_KEY_HEX);
        String otherKey = "fedcba98765432100123456789abcdef";
        assertThrows(IllegalArgumentException.class, () -> Sm4Crypto.decrypt(ciphertext, otherKey));
    }

    /**
     * 密文被篡改后解密失败
     */
    @Test
    @DisplayName("解密：密文篡改抛出异常")
    void shouldFailWhenTampered() {
        String ciphertext = Sm4Crypto.encrypt("secret", SAMPLE_KEY_HEX);
        String tampered = ciphertext.substring(0, ciphertext.length() - 1)
                + (ciphertext.endsWith("a") ? "b" : "a");
        assertThrows(IllegalArgumentException.class, () -> Sm4Crypto.decrypt(tampered, SAMPLE_KEY_HEX));
    }

    /**
     * 生成密钥长度与格式
     */
    @Test
    @DisplayName("密钥：generateKey 返回 32 位十六进制")
    void shouldGenerateValidKey() {
        String key = Sm4Crypto.generateKey();
        assertEquals(32, key.length());
        assertEquals(16, HexFormat.of().parseHex(key).length);
    }

    /**
     * 非法密钥与密文被拒绝
     */
    @Test
    @DisplayName("参数校验：非法密钥/密文抛出异常")
    void shouldRejectInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> Sm4Crypto.encrypt("data".getBytes(StandardCharsets.UTF_8), new byte[8]));
        assertThrows(IllegalArgumentException.class, () -> Sm4Crypto.encrypt("data", "1234"));
        assertThrows(IllegalArgumentException.class, () -> Sm4Crypto.decrypt("zzzz", SAMPLE_KEY_HEX));
        assertThrows(IllegalArgumentException.class, () -> Sm4Crypto.decrypt("00", SAMPLE_KEY_HEX));
    }
}

package com.bluewhale.security.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AesCrypto} 单元测试
 *
 * @author BlueWhale
 */
class AesCryptoTest {

    /**
     * 文本加解密往返
     */
    @Test
    @DisplayName("加密/解密：文本往返一致")
    void shouldRoundTripText() {
        String key = AesCrypto.generateKey();
        String plaintext = "蓝鲸平台敏感数据 2026";
        String ciphertext = AesCrypto.encrypt(plaintext, key);
        assertNotEquals(plaintext, ciphertext);
        assertEquals(plaintext, AesCrypto.decrypt(ciphertext, key));
    }

    /**
     * 随机 IV：相同明文两次密文不同
     */
    @Test
    @DisplayName("随机 IV：相同明文产生不同密文且均可解密")
    void shouldUseRandomIv() {
        String key = AesCrypto.generateKey();
        String first = AesCrypto.encrypt("same", key);
        String second = AesCrypto.encrypt("same", key);
        assertNotEquals(first, second);
        assertEquals("same", AesCrypto.decrypt(first, key));
        assertEquals("same", AesCrypto.decrypt(second, key));
    }

    /**
     * 错误密钥解密失败
     */
    @Test
    @DisplayName("解密：错误密钥抛出异常")
    void shouldFailWithWrongKey() {
        String ciphertext = AesCrypto.encrypt("secret", AesCrypto.generateKey());
        assertThrows(IllegalStateException.class, () -> AesCrypto.decrypt(ciphertext, AesCrypto.generateKey()));
    }

    /**
     * 密文被篡改后解密失败
     */
    @Test
    @DisplayName("解密：密文篡改抛出异常")
    void shouldFailWhenTampered() {
        String key = AesCrypto.generateKey();
        byte[] ciphertext = Base64.getDecoder().decode(AesCrypto.encrypt("secret", key));
        ciphertext[ciphertext.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(ciphertext);
        assertThrows(IllegalStateException.class, () -> AesCrypto.decrypt(tampered, key));
    }

    /**
     * 生成密钥长度为 256 位
     */
    @Test
    @DisplayName("密钥：generateKey 返回 32 字节")
    void shouldGenerate256BitKey() {
        byte[] key = Base64.getDecoder().decode(AesCrypto.generateKey());
        assertEquals(32, key.length);
    }

    /**
     * 非法密钥长度被拒绝
     */
    @Test
    @DisplayName("密钥：非 16/24/32 字节抛出异常")
    void shouldRejectInvalidKeyLength() {
        byte[] key = new byte[15];
        assertThrows(IllegalArgumentException.class,
                () -> AesCrypto.encrypt("data".getBytes(StandardCharsets.UTF_8), key));
    }

    /**
     * 空密文被拒绝
     */
    @Test
    @DisplayName("解密：空密文抛出异常")
    void shouldRejectBlankCiphertext() {
        String key = AesCrypto.generateKey();
        assertThrows(IllegalArgumentException.class, () -> AesCrypto.decrypt(" ", key));
    }
}

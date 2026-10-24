package com.bluewhale.security.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RsaCrypto} 单元测试
 *
 * @author BlueWhale
 */
class RsaCryptoTest {

    /** 测试密钥对（2048 位，类级复用降低耗时） */
    private static KeyPair keyPair;

    /**
     * 初始化测试密钥对
     */
    @BeforeAll
    static void initKeyPair() {
        keyPair = RsaCrypto.generateKeyPair();
    }

    /**
     * 公钥加密、私钥解密往返
     */
    @Test
    @DisplayName("加密/解密：文本往返一致")
    void shouldRoundTripText() {
        String ciphertext = RsaCrypto.encrypt("蓝鲸平台敏感数据", keyPair.getPublic());
        assertEquals("蓝鲸平台敏感数据", RsaCrypto.decrypt(ciphertext, keyPair.getPrivate()));
    }

    /**
     * 签名与验签
     */
    @Test
    @DisplayName("签名/验签：原文通过、篡改失败")
    void shouldSignAndVerify() {
        String signature = RsaCrypto.sign("order-1001", keyPair.getPrivate());
        assertTrue(RsaCrypto.verify("order-1001", signature, keyPair.getPublic()));
        assertFalse(RsaCrypto.verify("order-1002", signature, keyPair.getPublic()));
    }

    /**
     * 密钥 Base64 序列化往返
     */
    @Test
    @DisplayName("密钥编码：X.509/PKCS#8 Base64 往返一致")
    void shouldEncodeAndDecodeKeys() {
        String publicKeyText = RsaCrypto.encodeKey(keyPair.getPublic());
        String privateKeyText = RsaCrypto.encodeKey(keyPair.getPrivate());
        PublicKey publicKey = RsaCrypto.decodePublicKey(publicKeyText);
        PrivateKey privateKey = RsaCrypto.decodePrivateKey(privateKeyText);
        assertNotNull(publicKey);
        assertNotNull(privateKey);
        String ciphertext = RsaCrypto.encrypt("payload", publicKey);
        assertEquals("payload", RsaCrypto.decrypt(ciphertext, privateKey));
    }

    /**
     * 弱密钥长度被拒绝
     */
    @Test
    @DisplayName("密钥生成：低于 2048 位抛出异常")
    void shouldRejectWeakKeySize() {
        assertThrows(IllegalArgumentException.class, () -> RsaCrypto.generateKeyPair(1024));
    }

    /**
     * 错误私钥解密失败
     */
    @Test
    @DisplayName("解密：错误私钥抛出异常")
    void shouldFailWithWrongPrivateKey() {
        String ciphertext = RsaCrypto.encrypt("payload", keyPair.getPublic());
        KeyPair other = RsaCrypto.generateKeyPair();
        assertThrows(IllegalStateException.class, () -> RsaCrypto.decrypt(ciphertext, other.getPrivate()));
    }

    /**
     * 非法密钥文本被拒绝
     */
    @Test
    @DisplayName("密钥解析：空白文本抛出异常")
    void shouldRejectBlankKeyText() {
        assertThrows(IllegalArgumentException.class, () -> RsaCrypto.decodePublicKey(" "));
        assertThrows(IllegalArgumentException.class, () -> RsaCrypto.decodePrivateKey(null));
    }
}

package com.bluewhale.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

/**
 * RSA 加密工具
 * <p>提供 2048 位密钥对生成、OAEP-SHA256 加解密、SHA256withRSA 签名验签，
 * 以及 X.509（公钥）/ PKCS#8（私钥）DER 的 Base64 序列化。</p>
 * <p><b>禁止</b>使用 PKCS#1 v1.5 填充与 MD5 签名；私钥不得写入日志或返回前端。</p>
 *
 * @author BlueWhale
 */
public final class RsaCrypto {

    /** 密钥算法 */
    private static final String ALGORITHM = "RSA";

    /** 加密变换：OAEP + SHA-256 */
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /** 签名算法 */
    private static final String SIGN_ALGORITHM = "SHA256withRSA";

    /** 默认密钥长度（位） */
    public static final int DEFAULT_KEY_SIZE = 2048;

    /**
     * 私有构造器，禁止实例化
     */
    private RsaCrypto() {
    }

    /**
     * 生成默认长度（2048 位）密钥对
     *
     * @return 密钥对
     */
    public static KeyPair generateKeyPair() {
        return generateKeyPair(DEFAULT_KEY_SIZE);
    }

    /**
     * 生成指定长度密钥对
     *
     * @param keySize 密钥长度（位），建议 ≥2048
     * @return 密钥对
     */
    public static KeyPair generateKeyPair(int keySize) {
        if (keySize < DEFAULT_KEY_SIZE) {
            throw new IllegalArgumentException("RSA 密钥长度不得小于 " + DEFAULT_KEY_SIZE + " 位");
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(keySize);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("RSA 密钥对生成失败", ex);
        }
    }

    /**
     * 公钥加密
     *
     * @param plaintext 明文
     * @param publicKey 公钥
     * @return Base64 密文
     */
    public static String encrypt(String plaintext, PublicKey publicKey) {
        if (plaintext == null || publicKey == null) {
            throw new IllegalArgumentException("明文与公钥不能为空");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("RSA 加密失败", ex);
        }
    }

    /**
     * 私钥解密
     *
     * @param ciphertext Base64 密文
     * @param privateKey 私钥
     * @return 明文
     */
    public static String decrypt(String ciphertext, PrivateKey privateKey) {
        if (ciphertext == null || ciphertext.isBlank() || privateKey == null) {
            throw new IllegalArgumentException("密文与私钥不能为空");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("RSA 解密失败（私钥不匹配或密文非法）", ex);
        }
    }

    /**
     * 私钥签名（SHA256withRSA）
     *
     * @param data       待签数据
     * @param privateKey 私钥
     * @return Base64 签名值
     */
    public static String sign(String data, PrivateKey privateKey) {
        if (data == null || privateKey == null) {
            throw new IllegalArgumentException("待签数据与私钥不能为空");
        }
        try {
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("RSA 签名失败", ex);
        }
    }

    /**
     * 公钥验签
     *
     * @param data      原始数据
     * @param signature Base64 签名值
     * @param publicKey 公钥
     * @return 验签通过返回 true
     */
    public static boolean verify(String data, String signature, PublicKey publicKey) {
        if (data == null || signature == null || publicKey == null) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance(SIGN_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(data.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * 将密钥编码为 Base64（公钥 X.509 / 私钥 PKCS#8）
     *
     * @param key 公钥或私钥
     * @return Base64 字符串
     */
    public static String encodeKey(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("密钥不能为空");
        }
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 从 Base64（X.509）还原公钥
     *
     * @param base64Key Base64 公钥
     * @return 公钥
     */
    public static PublicKey decodePublicKey(String base64Key) {
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decode(base64Key));
            return KeyFactory.getInstance(ALGORITHM).generatePublic(spec);
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("公钥解析失败", ex);
        }
    }

    /**
     * 从 Base64（PKCS#8）还原私钥
     *
     * @param base64Key Base64 私钥
     * @return 私钥
     */
    public static PrivateKey decodePrivateKey(String base64Key) {
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decode(base64Key));
            return KeyFactory.getInstance(ALGORITHM).generatePrivate(spec);
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("私钥解析失败", ex);
        }
    }

    /**
     * 解码 Base64 密钥文本
     *
     * @param base64Key Base64 文本
     * @return 原始字节
     */
    private static byte[] decode(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("密钥文本不能为空");
        }
        return Base64.getDecoder().decode(base64Key);
    }
}

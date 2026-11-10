package com.bluewhale.common.security.crypto;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.jasypt.iv.RandomIvGenerator;

/**
 * 配置值加解密（REQ-SEC-020）：以 Jasypt {@link StandardPBEStringEncryptor} 实现
 * {@code ENC(...)} 密文的生成与解密，按需创建加密器并复用。
 *
 * <p>安全约束：密钥为空、过短或为占位值时直接拒绝，不做降级明文；解密失败只报配置项名，
 * 不回显密文与密钥。</p>
 *
 * @author BlueWhale
 */
public final class ConfigValueCipher {

    /**
     * 明文完整性哨兵：Jasypt PBE 算法本身不做认证，错密钥解密出随机字节时有概率不触发填充异常；
     * 加密前为明文加上固定哨兵，解密后校验哨兵缺失即视为密钥错误，避免静默返回乱码。
     */
    private static final String INTEGRITY_SENTINEL = "whale-enc:";

    /** 加密设置 */
    private final ConfigEncryptionSettings settings;

    /** Jasypt 加密器（懒加载；构造代价较高但线程安全） */
    private volatile StringEncryptor encryptor;

    /**
     * 构造器
     *
     * @param settings 配置加密设置
     */
    public ConfigValueCipher(ConfigEncryptionSettings settings) {
        this.settings = settings;
    }

    /**
     * 若属性值为 {@code ENC(...)} 密文则解密，否则原样返回（支持非字符串类型）
     *
     * @param propertyName 属性名（仅用于报错定位，不输出值）
     * @param rawValue 原始属性值
     * @return 解密后的值；非密文返回原值
     */
    public Object decryptIfNeeded(String propertyName, Object rawValue) {
        if (!(rawValue instanceof String text) || !settings.isEncrypted(text)) {
            return rawValue;
        }
        return decrypt(settings.unwrap(text), propertyName);
    }

    /**
     * 解密 {@code ENC(...)} 内的密文主体
     *
     * @param cipherText 密文主体（不含前后缀）
     * @param propertyName 属性名（仅供报错定位）
     * @return 明文
     */
    public String decrypt(String cipherText, String propertyName) {
        settings.requireUsableKey();
        try {
            String plainText = encryptor().decrypt(cipherText);
            if (!plainText.startsWith(INTEGRITY_SENTINEL)) {
                throw new IllegalStateException("完整性校验失败");
            }
            return plainText.substring(INTEGRITY_SENTINEL.length());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("配置项 [" + propertyName + "] 的 ENC(...) 密文解密失败，"
                    + "请检查 " + ConfigEncryptionSettings.ENV_KEY + " 与算法配置是否与加密时一致", exception);
        }
    }

    /**
     * 生成 {@code ENC(...)} 密文（明文前附加完整性哨兵）
     *
     * @param plainText 明文
     * @return 带前后缀的密文
     */
    public String encrypt(String plainText) {
        settings.requireUsableKey();
        return settings.getPrefix() + encryptor().encrypt(INTEGRITY_SENTINEL + plainText) + settings.getSuffix();
    }

    /**
     * 懒加载 Jasypt 加密器：随机 IV + 指定 PBE 算法
     *
     * @return 线程安全的加密器
     */
    private StringEncryptor encryptor() {
        StringEncryptor current = encryptor;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (encryptor == null) {
                SimpleStringPBEConfig config = new SimpleStringPBEConfig();
                config.setPassword(settings.getPassword());
                config.setAlgorithm(settings.getAlgorithm());
                config.setIvGenerator(new RandomIvGenerator());
                StandardPBEStringEncryptor created = new StandardPBEStringEncryptor();
                created.setConfig(config);
                encryptor = created;
            }
            return encryptor;
        }
    }
}

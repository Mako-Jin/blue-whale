package com.bluewhale.common.security.crypto;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * 密文属性源包装（REQ-SEC-020）：对委托属性源的每次读取做 {@code ENC(...)} 解密，
 * 非密文值原样返回，属性源名称与顺序保持不变。
 *
 * @author BlueWhale
 */
public final class EncryptedPropertySource extends PropertySource<PropertySource<?>> {

    /** 被包装的原始属性源 */
    private final PropertySource<?> delegate;

    /** 配置值解密器 */
    private final ConfigValueCipher cipher;

    /**
     * 构造器
     *
     * @param delegate 被包装的原始属性源
     * @param cipher 配置值解密器
     */
    private EncryptedPropertySource(PropertySource<?> delegate, ConfigValueCipher cipher) {
        super(delegate.getName(), delegate);
        this.delegate = delegate;
        this.cipher = cipher;
    }

    /**
     * 按原始属性源类型选择包装实现：可枚举源保留属性名枚举能力，其余使用普通包装
     *
     * @param source 原始属性源
     * @param cipher 配置值解密器
     * @return 包装后的属性源；已是包装类型时原样返回
     */
    public static PropertySource<?> wrap(PropertySource<?> source, ConfigValueCipher cipher) {
        if (source instanceof EncryptedPropertySource || source instanceof EncryptedEnumerablePropertySource) {
            return source;
        }
        if (source instanceof EnumerablePropertySource<?> enumerable) {
            return new EncryptedEnumerablePropertySource(enumerable, cipher);
        }
        return new EncryptedPropertySource(source, cipher);
    }

    /**
     * 判定属性源是否已包装，避免重复包装
     *
     * @param source 原始属性源
     * @return 已包装返回 true
     */
    public static boolean isWrapped(PropertySource<?> source) {
        return source instanceof EncryptedPropertySource || source instanceof EncryptedEnumerablePropertySource;
    }

    @Override
    public boolean containsProperty(String name) {
        return delegate.containsProperty(name);
    }

    @Override
    public Object getProperty(String name) {
        return cipher.decryptIfNeeded(name, delegate.getProperty(name));
    }
}

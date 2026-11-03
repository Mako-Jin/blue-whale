package com.bluewhale.common.security.crypto;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * 可枚举密文属性源包装（REQ-SEC-020）：保留 {@link EnumerablePropertySource#getPropertyNames()}
 * 语义，使 Spring Boot 在扫描属性名（如配置绑定、条件装配）时行为不退化。
 *
 * @author BlueWhale
 */
public final class EncryptedEnumerablePropertySource extends EnumerablePropertySource<PropertySource<?>> {

    /** 被包装的原始可枚举属性源 */
    private final EnumerablePropertySource<?> delegate;

    /** 配置值解密器 */
    private final ConfigValueCipher cipher;

    /**
     * 构造器
     *
     * @param delegate 被包装的原始可枚举属性源
     * @param cipher 配置值解密器
     */
    EncryptedEnumerablePropertySource(EnumerablePropertySource<?> delegate, ConfigValueCipher cipher) {
        super(delegate.getName(), delegate);
        this.delegate = delegate;
        this.cipher = cipher;
    }

    @Override
    public String[] getPropertyNames() {
        return delegate.getPropertyNames();
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

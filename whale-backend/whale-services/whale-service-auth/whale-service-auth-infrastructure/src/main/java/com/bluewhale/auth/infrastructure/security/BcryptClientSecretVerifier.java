package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.application.port.ClientSecretVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 客户端密钥哈希适配器：以 Spring Security {@link PasswordEncoder}（BCrypt）实现应用层密钥端口。
 *
 * @author BlueWhale
 */
@Component
public class BcryptClientSecretVerifier implements ClientSecretVerifier {

    /** Spring Security 口令编码器（与用户口令共用同一 BCrypt 配置） */
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造器注入
     *
     * @param passwordEncoder Spring Security 口令编码器
     */
    public BcryptClientSecretVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String rawSecret) {
        return passwordEncoder.encode(rawSecret);
    }

    @Override
    public boolean matches(String rawSecret, String hashedSecret) {
        return rawSecret != null && hashedSecret != null
                && passwordEncoder.matches(rawSecret, hashedSecret);
    }
}

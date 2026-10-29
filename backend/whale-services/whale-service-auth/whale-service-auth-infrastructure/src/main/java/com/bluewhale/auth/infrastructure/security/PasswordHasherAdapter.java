package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.application.user.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 口令加密适配器：以 Spring Security {@link PasswordEncoder}（BCrypt）实现应用层加密端口。
 *
 * @author BlueWhale
 */
@Component
public class PasswordHasherAdapter implements PasswordHasher {

    /** Spring Security 口令编码器 */
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造器注入
     *
     * @param passwordEncoder Spring Security 口令编码器
     */
    public PasswordHasherAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}

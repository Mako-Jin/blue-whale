package com.bluewhale.auth.bootstrap;

import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AdminPasswordInitializer} 单元测试（T-03 去除默认管理员口令）：
 * 出厂种子密文检测、随机轮换与强制改密标记、环境变量覆盖与开关。
 *
 * <p>纯 Mockito 单测，不启动 Spring 上下文、不连库。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AdminPasswordInitializerTest {

    private static final long ADMIN_ID = AdminPasswordInitializer.ADMIN_USER_ID;
    private static final String SEED_CIPHER = AdminPasswordInitializer.SEED_ADMIN_PASSWORD_CIPHER;
    private static final String NEW_CIPHER = "$2a$10$newencodedciphervalue";
    private static final String ENV_PASSWORD = "EnvOverride#2026";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AdminPasswordProperties properties;

    @Mock
    private Environment environment;

    @InjectMocks
    private AdminPasswordInitializer initializer;

    private static User admin(String cipher) {
        return User.reconstitute(ADMIN_ID, 1L, Username.of("admin"), EncryptedPassword.of(cipher),
                "超级管理员", null, null, null, null, User.STATUS_ENABLED, null, null,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    @DisplayName("出厂种子密文：轮换为随机口令、置强制改密标记并落库")
    void shouldRotateWhenSeedCipherDetected() {
        when(properties.isRotateOnDefault()).thenReturn(true);
        when(userRepository.findByIdAcrossTenants(ADMIN_ID)).thenReturn(Optional.of(admin(SEED_CIPHER)));
        when(passwordEncoder.encode(anyString())).thenReturn(NEW_CIPHER);

        initializer.run(null);

        // 随机口令：16 位、含大小写与数字、剔除易混淆字符
        ArgumentCaptor<String> rawPassword = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(rawPassword.capture());
        String plaintext = rawPassword.getValue();
        assertEquals(16, plaintext.length());
        assertTrue(plaintext.matches("[A-Za-z0-9]+"), "随机口令应仅含字母数字: " + plaintext);
        assertTrue(plaintext.chars().anyMatch(Character::isUpperCase));
        assertTrue(plaintext.chars().anyMatch(Character::isLowerCase));
        assertTrue(plaintext.chars().anyMatch(Character::isDigit));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(NEW_CIPHER, saved.getValue().getPassword().value());
        assertNotEquals(SEED_CIPHER, saved.getValue().getPassword().value());
        assertTrue(saved.getValue().isMustChangePassword(), "轮换后必须置强制首登改密标记");
    }

    @Test
    @DisplayName("非出厂种子密文：已轮换过，幂等无操作")
    void shouldSkipWhenCipherAlreadyRotated() {
        when(properties.isRotateOnDefault()).thenReturn(true);
        when(userRepository.findByIdAcrossTenants(ADMIN_ID))
                .thenReturn(Optional.of(admin("$2a$10$alreadyrotatedcipher")));

        initializer.run(null);

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("环境变量 WHALE_AUTH_ADMIN_INIT_PASSWORD 设置时覆盖随机口令并置标记")
    void shouldUseEnvPasswordWhenPresent() {
        when(properties.isRotateOnDefault()).thenReturn(true);
        when(userRepository.findByIdAcrossTenants(ADMIN_ID)).thenReturn(Optional.of(admin(SEED_CIPHER)));
        when(environment.getProperty(AdminPasswordInitializer.ENV_ADMIN_INIT_PASSWORD)).thenReturn(ENV_PASSWORD);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        when(passwordEncoder.encode(ENV_PASSWORD)).thenReturn(NEW_CIPHER);

        initializer.run(null);

        verify(passwordEncoder).encode(ENV_PASSWORD);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(NEW_CIPHER, saved.getValue().getPassword().value());
        assertTrue(saved.getValue().isMustChangePassword());
    }

    @Test
    @DisplayName("开关关闭：rotate-on-default=false 时整体跳过")
    void shouldSkipWhenRotationDisabled() {
        when(properties.isRotateOnDefault()).thenReturn(false);

        initializer.run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }
}

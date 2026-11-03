package com.bluewhale.auth.application.user.password;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TemporaryPasswordGenerator} 单元测试（REQ-SEC-023）：管理员重置口令时的一次性口令生成。
 *
 * <p>断言默认长度、策略最小长度适配与字符类别覆盖（大小写字母、数字、特殊字符）。</p>
 *
 * @author BlueWhale
 */
class TemporaryPasswordGeneratorTest {

    private static final int DEFAULT_LENGTH = 16;

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    @DisplayName("generate：默认生成 16 位且包含四类字符")
    void shouldGenerateDefaultLengthPassword() {
        String password = generator.generate();

        assertEquals(DEFAULT_LENGTH, password.length());
        assertTrue(password.chars().anyMatch(Character::isUpperCase));
        assertTrue(password.chars().anyMatch(Character::isLowerCase));
        assertTrue(password.chars().anyMatch(Character::isDigit));
        assertTrue(password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch)));
    }

    @Test
    @DisplayName("generate：策略最小长度大于 16 时按策略长度生成")
    void shouldRespectLongerMinimumLength() {
        String password = generator.generate(24);

        assertEquals(24, password.length());
    }

    @Test
    @DisplayName("generate：策略最小长度小于 16 时仍按默认长度生成")
    void shouldKeepDefaultLengthWhenMinimumSmaller() {
        String password = generator.generate(8);

        assertEquals(DEFAULT_LENGTH, password.length());
    }

    @Test
    @DisplayName("generate：多次生成结果不同（安全随机源）")
    void shouldGenerateDifferentPasswords() {
        String first = generator.generate();
        String second = generator.generate();

        assertTrue(!first.equals(second));
    }
}

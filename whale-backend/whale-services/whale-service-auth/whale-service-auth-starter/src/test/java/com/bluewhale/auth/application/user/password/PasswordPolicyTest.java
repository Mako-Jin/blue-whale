package com.bluewhale.auth.application.user.password;

import com.bluewhale.auth.infrastructure.config.PasswordPolicyProperties;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.bluewhale.auth.application.model.PasswordPolicy;

/**
 * {@link PasswordPolicy} 口令强度策略单元测试（REQ-SEC-021）。
 *
 * <p>覆盖长度、四类字符、弱口令与用户名包含校验，以及关闭字符类别要求后的放宽行为。</p>
 *
 * @author BlueWhale
 */
class PasswordPolicyTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final String USERNAME = "alice_01";

    /**
     * 构造默认策略（与 {@link PasswordPolicyProperties} 默认值一致）
     *
     * @return 默认强度策略
     */
    private static PasswordPolicy defaultPolicy() {
        return PasswordPolicy.from(new PasswordPolicyProperties());
    }

    @Test
    @DisplayName("默认策略：满足长度与四类字符的强口令通过校验")
    void shouldAcceptStrongPassword() {
        assertDoesNotThrow(() -> defaultPolicy().validate("Str0ng@Passw0rd", USERNAME));
    }

    @Test
    @DisplayName("空口令：抛出参数错误")
    void shouldRejectBlankPassword() {
        BizException ex = assertThrows(BizException.class,
                () -> defaultPolicy().validate("   ", USERNAME));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("口令不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("长度不足默认 12 位：抛出参数错误")
    void shouldRejectTooShortPassword() {
        BizException ex = assertThrows(BizException.class,
                () -> defaultPolicy().validate("Ab1@xyz", USERNAME));

        assertEquals("口令长度至少 12 位", ex.getMessage());
    }

    @Test
    @DisplayName("长度超过上限：抛出参数错误")
    void shouldRejectTooLongPassword() {
        PasswordPolicyProperties properties = new PasswordPolicyProperties();
        properties.setMaxLength(16);
        PasswordPolicy policy = PasswordPolicy.from(properties);

        BizException ex = assertThrows(BizException.class,
                () -> policy.validate("Ab1@abcdefghijklmnopqrst", USERNAME));

        assertEquals("口令长度不能超过 16 位", ex.getMessage());
    }

    @Test
    @DisplayName("缺少大写字母：抛出参数错误")
    void shouldRequireUpperLetter() {
        BizException ex = assertThrows(BizException.class,
                () -> defaultPolicy().validate("str0ng@passw0rd", USERNAME));

        assertEquals("口令需包含大写字母", ex.getMessage());
    }

    @Test
    @DisplayName("缺少小写字母：抛出参数错误")
    void shouldRequireLowerLetter() {
        BizException ex = assertThrows(BizException.class,
                () -> defaultPolicy().validate("STR0NG@PASSW0RD", USERNAME));

        assertEquals("口令需包含小写字母", ex.getMessage());
    }

    @Test
    @DisplayName("缺少数字：抛出参数错误")
    void shouldRequireDigit() {
        BizException ex = assertThrows(BizException.class,
                () -> defaultPolicy().validate("Strong@Password", USERNAME));

        assertEquals("口令需包含数字", ex.getMessage());
    }

    @Test
    @DisplayName("缺少特殊字符：抛出参数错误")
    void shouldRequireSpecialCharacter() {
        BizException ex = assertThrows(BizException.class,
                () -> defaultPolicy().validate("Str0ngPassw0rd", USERNAME));

        assertEquals("口令需包含特殊字符", ex.getMessage());
    }

    @Test
    @DisplayName("常见弱口令：抛出参数错误")
    void shouldRejectWeakPassword() {
        PasswordPolicyProperties properties = new PasswordPolicyProperties();
        properties.setMinLength(8);
        properties.setRequireUpper(false);
        properties.setRequireSpecial(false);
        PasswordPolicy policy = PasswordPolicy.from(properties);

        BizException ex = assertThrows(BizException.class,
                () -> policy.validate("passw0rd", USERNAME));

        assertEquals("口令过于常见，请更换更复杂的口令", ex.getMessage());
    }

    @Test
    @DisplayName("口令包含用户名：抛出参数错误")
    void shouldRejectPasswordContainingUsername() {
        BizException ex = assertThrows(BizException.class,
                () -> defaultPolicy().validate("Alice_01@Strong1", USERNAME));

        assertEquals("口令不得包含用户名", ex.getMessage());
    }

    @Test
    @DisplayName("关闭字符类别要求：仅校验长度与弱口令")
    void shouldRelaxCharacterClassRequirements() {
        PasswordPolicyProperties properties = new PasswordPolicyProperties();
        properties.setRequireUpper(false);
        properties.setRequireLower(false);
        properties.setRequireDigit(false);
        properties.setRequireSpecial(false);

        assertDoesNotThrow(() -> PasswordPolicy.from(properties).validate("abcdefghijklm", USERNAME));
    }
}

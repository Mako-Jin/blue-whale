package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.auth.infrastructure.config.PasswordPolicyProperties;
import com.bluewhale.common.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link PasswordPolicy} 口令强度策略单元测试（REQ-SEC-021）。
 *
 * <p>覆盖长度、四类字符、弱口令、用户名互含（正/反方向）校验，以及关闭字符类别要求后的放宽行为。</p>
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
        return policy(new PasswordPolicyProperties());
    }

    /**
     * 将配置属性映射为领域策略（模拟应用层 {@code toDomainPolicy} 的映射口径）
     *
     * @param p 配置属性
     * @return 领域强度策略
     */
    private static PasswordPolicy policy(PasswordPolicyProperties p) {
        return new PasswordPolicy(p.getMinLength(), p.getMaxLength(), p.isRequireUpper(),
                p.isRequireLower(), p.isRequireDigit(), p.isRequireSpecial());
    }

    @Test
    @DisplayName("默认策略：满足长度与四类字符的强口令通过校验")
    void shouldAcceptStrongPassword() {
        assertDoesNotThrow(() -> defaultPolicy().validate("Str0ng@Passw0rd", USERNAME));
    }

    @Test
    @DisplayName("空口令：抛出领域异常")
    void shouldRejectBlankPassword() {
        DomainException ex = assertThrows(DomainException.class,
                () -> defaultPolicy().validate("   ", USERNAME));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("口令不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("长度不足默认 12 位：抛出领域异常")
    void shouldRejectTooShortPassword() {
        DomainException ex = assertThrows(DomainException.class,
                () -> defaultPolicy().validate("Ab1@xyz", USERNAME));

        assertEquals("口令长度至少 12 位", ex.getMessage());
    }

    @Test
    @DisplayName("长度超过上限：抛出领域异常")
    void shouldRejectTooLongPassword() {
        PasswordPolicyProperties properties = new PasswordPolicyProperties();
        properties.setMaxLength(16);
        PasswordPolicy policy = policy(properties);

        DomainException ex = assertThrows(DomainException.class,
                () -> policy.validate("Ab1@abcdefghijklmnopqrst", USERNAME));

        assertEquals("口令长度不能超过 16 位", ex.getMessage());
    }

    @Test
    @DisplayName("缺少大写字母：抛出领域异常")
    void shouldRequireUpperLetter() {
        DomainException ex = assertThrows(DomainException.class,
                () -> defaultPolicy().validate("str0ng@passw0rd", USERNAME));

        assertEquals("口令需包含大写字母", ex.getMessage());
    }

    @Test
    @DisplayName("缺少小写字母：抛出领域异常")
    void shouldRequireLowerLetter() {
        DomainException ex = assertThrows(DomainException.class,
                () -> defaultPolicy().validate("STR0NG@PASSW0RD", USERNAME));

        assertEquals("口令需包含小写字母", ex.getMessage());
    }

    @Test
    @DisplayName("缺少数字：抛出领域异常")
    void shouldRequireDigit() {
        DomainException ex = assertThrows(DomainException.class,
                () -> defaultPolicy().validate("Strong@Password", USERNAME));

        assertEquals("口令需包含数字", ex.getMessage());
    }

    @Test
    @DisplayName("缺少特殊字符：抛出领域异常")
    void shouldRequireSpecialCharacter() {
        DomainException ex = assertThrows(DomainException.class,
                () -> defaultPolicy().validate("Str0ngPassw0rd", USERNAME));

        assertEquals("口令需包含特殊字符", ex.getMessage());
    }

    @Test
    @DisplayName("常见弱口令：抛出领域异常")
    void shouldRejectWeakPassword() {
        PasswordPolicyProperties properties = new PasswordPolicyProperties();
        properties.setMinLength(8);
        properties.setRequireUpper(false);
        properties.setRequireSpecial(false);
        PasswordPolicy policy = policy(properties);

        DomainException ex = assertThrows(DomainException.class,
                () -> policy.validate("passw0rd", USERNAME));

        assertEquals("口令过于常见，请更换更复杂的口令", ex.getMessage());
    }

    @Test
    @DisplayName("口令包含用户名：抛出领域异常")
    void shouldRejectPasswordContainingUsername() {
        DomainException ex = assertThrows(DomainException.class,
                () -> defaultPolicy().validate("Alice_01@Strong1", USERNAME));

        assertEquals("口令不得包含用户名", ex.getMessage());
    }

    @Test
    @DisplayName("用户名包含口令（反转方向）：抛出领域异常")
    void shouldRejectUsernameContainingPassword() {
        PasswordPolicyProperties properties = new PasswordPolicyProperties();
        properties.setMinLength(8);
        PasswordPolicy policy = policy(properties);

        DomainException ex = assertThrows(DomainException.class,
                () -> policy.validate("Zebra9#x", "ops_zebra9#x_account"));

        assertEquals("用户名不得包含口令", ex.getMessage());
    }

    @Test
    @DisplayName("用户名与口令互不包含：通过校验")
    void shouldAcceptPasswordAndUsernameNotContainingEachOther() {
        PasswordPolicyProperties properties = new PasswordPolicyProperties();
        properties.setMinLength(8);
        assertDoesNotThrow(() -> policy(properties).validate("Zebra9#x", "alice_01"));
    }

    @Test
    @DisplayName("关闭字符类别要求：仅校验长度与弱口令")
    void shouldRelaxCharacterClassRequirements() {
        PasswordPolicyProperties properties = new PasswordPolicyProperties();
        properties.setRequireUpper(false);
        properties.setRequireLower(false);
        properties.setRequireDigit(false);
        properties.setRequireSpecial(false);

        assertDoesNotThrow(() -> policy(properties).validate("abcdefghijklm", USERNAME));
    }
}

package com.bluewhale.sts.auth.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 口令强度策略测试。
 *
 * <p>不需要 Spring：{@link PasswordPolicy} 是纯函数式的，直接 {@code new} 即可。
 * 这正是把它从 {@code UserService} 里拆出来的目的 —— 强度规则的边界条件
 * （缺哪一类、报几项、配置改了描述跟不跟着改）能在这里一条条钉死，
 * 而不必为每条规则都构造一次 HTTP 请求。
 */
class PasswordPolicyTest {

    private static PasswordPolicy policy(AuthProperties properties) {
        return new PasswordPolicy(properties);
    }

    private static AuthProperties defaults() {
        return new AuthProperties();
    }

    @Test
    @DisplayName("满足默认策略的口令没有任何违反项")
    void shouldAcceptCompliantPassword() {
        assertTrue(policy(defaults()).violations("Abcd1234").isEmpty());
    }

    @Test
    @DisplayName("空口令只报\"不能为空\"，不再叠加其它缺项")
    void shouldRejectEmptyWithoutPilingOn() {
        // 空串连"长度"都谈不上，报一堆派生出来的缺项只会淹没真正的原因
        assertEquals(List.of("不能为空"), policy(defaults()).violations(""));
        assertEquals(List.of("不能为空"), policy(defaults()).violations(null));
    }

    @Test
    @DisplayName("一次报出全部缺失项，而不是只报第一条")
    void shouldReportAllViolationsAtOnce() {
        // "ab"：太短、无大写、无数字（小写有，所以不报）
        List<String> violations = policy(defaults()).violations("ab");

        assertEquals(3, violations.size(), violations.toString());
        assertTrue(violations.contains("长度至少 8 位"));
        assertTrue(violations.contains("至少一个大写字母"));
        assertTrue(violations.contains("至少一个数字"));
        assertFalse(violations.contains("至少一个小写字母"));
    }

    @Test
    @DisplayName("特殊字符默认不要求，打开后缺了必须报")
    void shouldHonorSpecialCharRequirement() {
        assertTrue(policy(defaults()).violations("Abcd1234").isEmpty());

        AuthProperties strict = defaults();
        strict.getPassword().setRequireSpecial(true);
        assertTrue(policy(strict).violations("Abcd1234").contains("至少一个特殊字符"));
        assertTrue(policy(strict).violations("Abcd@1234").isEmpty());
    }

    @Test
    @DisplayName("大小写按 Unicode 属性判定，不是正则的 [A-Z]")
    void shouldJudgeCaseByUnicodeProperty() {
        // À 是 U+00C0 的 Lu 类；正则 [A-Z] 判不出它，Character.isUpperCase 可以。
        // 这条钉的是"用哪种判定"，因为换成正则在中文/重音口令上会静默放宽。
        assertTrue(policy(defaults()).violations("Àbcd1234").isEmpty());
    }

    @Test
    @DisplayName("策略跟着配置走，describe() 也一起变")
    void shouldFollowConfiguration() {
        AuthProperties custom = defaults();
        custom.getPassword().setMinLength(12);
        custom.getPassword().setRequireUppercase(false);
        custom.getPassword().setRequireLowercase(false);
        custom.getPassword().setRequireDigit(false);

        PasswordPolicy policy = policy(custom);
        assertTrue(policy.violations("abcdefghijkl").isEmpty());
        assertTrue(policy.violations("abcdefghijk").contains("长度至少 12 位"));

        // 提示语必须跟着配置变，否则会出现"要求 12 位、提示还写着 8 位"的错位
        String described = policy.describe();
        assertTrue(described.contains("12~128"), described);
        assertFalse(described.contains("大写"), described);
    }

    @Test
    @DisplayName("historyCount 从配置读取，0 表示关闭防重用")
    void shouldExposeHistoryCount() {
        assertEquals(5, policy(defaults()).historyCount());

        AuthProperties off = defaults();
        off.getPassword().setHistoryCount(0);
        assertEquals(0, policy(off).historyCount());
    }

}

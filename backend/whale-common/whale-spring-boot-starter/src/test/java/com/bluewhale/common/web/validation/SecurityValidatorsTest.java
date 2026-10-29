package com.bluewhale.common.web.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XSS / SQL 注入参数校验器测试。
 *
 * @author BlueWhale
 */
class SecurityValidatorsTest {

    private final XssValidator xssValidator = new XssValidator();

    @Test
    @DisplayName("XSS：空值与普通文本通过")
    void shouldPassPlainText() {
        assertTrue(xssValidator.isValid(null, null));
        assertTrue(xssValidator.isValid("", null));
        assertTrue(xssValidator.isValid("蓝鲸科技 <团队> 你好", null));
        assertTrue(xssValidator.isValid("one=1, two=2", null));
    }

    @Test
    @DisplayName("XSS：脚本标签、事件属性与危险协议不通过")
    void shouldRejectXssPayloads() {
        assertFalse(xssValidator.isValid("<script>alert(1)</script>", null));
        assertFalse(xssValidator.isValid("<img src=x onerror=alert(1)>", null));
        assertFalse(xssValidator.isValid("javascript:alert(1)", null));
        assertFalse(xssValidator.isValid("<iframe src=//evil.example></iframe>", null));
        assertFalse(xssValidator.isValid("expression(alert(1))", null));
    }

    @Test
    @DisplayName("SQL：空值与普通文本通过")
    void shouldPassPlainSqlText() {
        assertFalse(SqlInjectionValidator.containsSqlInjection(null));
        assertFalse(SqlInjectionValidator.containsSqlInjection(""));
        assertFalse(SqlInjectionValidator.containsSqlInjection("订单编号 2025-001"));
        assertFalse(SqlInjectionValidator.containsSqlInjection("select the best match"));
    }

    @Test
    @DisplayName("SQL：注释符、恒等式条件与联合查询不通过")
    void shouldRejectSqlInjectionPayloads() {
        assertTrue(SqlInjectionValidator.containsSqlInjection("admin' -- "));
        assertTrue(SqlInjectionValidator.containsSqlInjection("1' or 1=1 --"));
        assertTrue(SqlInjectionValidator.containsSqlInjection("1 union select password from users"));
        assertTrue(SqlInjectionValidator.containsSqlInjection("x'; drop table users; --"));
    }
}

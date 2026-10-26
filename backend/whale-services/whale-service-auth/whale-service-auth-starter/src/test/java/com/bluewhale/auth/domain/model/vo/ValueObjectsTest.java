package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 领域值对象不变量单元测试（AUTH-14 / T-DDD-2）：非法值构造即抛错。
 *
 * @author BlueWhale
 */
class ValueObjectsTest {

    private static final int PARAM_ERROR_CODE = 400;

    @Test
    @DisplayName("Username：合法值可构造，非法值抛参数错误")
    void usernameShouldValidateInvariant() {
        assertEquals("alice_01", Username.of("alice_01").value());

        assertThrows(BizException.class, () -> Username.of(null));
        assertThrows(BizException.class, () -> Username.of(""));
        assertThrows(BizException.class, () -> Username.of("alice 01"));
        assertThrows(BizException.class, () -> Username.of("a".repeat(65)));
    }

    @Test
    @DisplayName("RoleCode：必须小写字母开头，非法值抛参数错误")
    void roleCodeShouldValidateInvariant() {
        assertEquals("super_admin", RoleCode.of("super_admin").value());

        assertThrows(BizException.class, () -> RoleCode.of("SuperAdmin"));
        assertThrows(BizException.class, () -> RoleCode.of("1admin"));
        assertThrows(BizException.class, () -> RoleCode.of("a"));
    }

    @Test
    @DisplayName("PermCode：至少两段冒号分隔的小写段落")
    void permCodeShouldValidateInvariant() {
        assertEquals("sys:user:list", PermCode.of("sys:user:list").value());

        assertThrows(BizException.class, () -> PermCode.of("sys"));
        assertThrows(BizException.class, () -> PermCode.of("sys:User:list"));
        assertThrows(BizException.class, () -> PermCode.of("sys:user:"));
    }

    @Test
    @DisplayName("EncryptedPassword：空值抛参数错误，toString 为掩码")
    void encryptedPasswordShouldMaskValue() {
        EncryptedPassword password = EncryptedPassword.of("$2a$10$abcdefghijklmnopqrstuv");

        assertFalse(password.toString().contains("$2a$10$"));
        assertEquals("EncryptedPassword(***)", password.toString());
        assertThrows(BizException.class, () -> EncryptedPassword.of(null));
        assertThrows(BizException.class, () -> EncryptedPassword.of(" "));
    }

    @Test
    @DisplayName("值对象：非法值抛出的错误码统一为 400")
    void shouldUseParamErrorCode() {
        BizException ex = assertThrows(BizException.class, () -> Username.of(""));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }
}

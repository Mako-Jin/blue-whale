package com.bluewhale.auth.domain.user.aggregate;

import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link User} 聚合不变量与状态流转单元测试（REQ-ARCH-001 / T-DDD-4）。
 *
 * @author BlueWhale
 */
class UserTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final EncryptedPassword CIPHER = EncryptedPassword.of("$2a$10$abcdefghijklmnopqrstuv");

    private static Username username() {
        return Username.of("alice_01");
    }

    @Test
    @DisplayName("create：合法构造，默认启用并回填各字段")
    void shouldCreateEnabledUserByDefault() {
        User user = User.create(username(), CIPHER, "Alice", "13800000000", "a@b.com", 3L, null);

        assertNull(user.getId());
        assertEquals("alice_01", user.getUsername().value());
        assertEquals("Alice", user.getNickname());
        assertEquals("13800000000", user.getPhone());
        assertEquals("a@b.com", user.getEmail());
        assertEquals(3L, user.getDeptId());
        assertEquals(User.STATUS_ENABLED, user.getStatus());
        assertTrue(user.isEnabled());
    }

    @Test
    @DisplayName("create：显式停用状态生效")
    void shouldCreateDisabledUserWhenStatusZero() {
        User user = User.create(username(), CIPHER, "Alice", null, null, null, 0);

        assertEquals(User.STATUS_DISABLED, user.getStatus());
        assertFalse(user.isEnabled());
    }

    @Test
    @DisplayName("不变量①：username 为空抛参数错误")
    void shouldRejectNullUsername() {
        BizException ex = assertThrows(BizException.class,
                () -> User.create(null, CIPHER, "Alice", null, null, null, 1));
        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("不变量②：口令非 BCrypt 密文抛参数错误")
    void shouldRejectNonBcryptPassword() {
        BizException ex = assertThrows(BizException.class,
                () -> User.create(username(), EncryptedPassword.of("plaintext"), "Alice", null, null, null, 1));
        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("不变量③：reconstitute 非法状态抛参数错误")
    void shouldRejectIllegalStatus() {
        assertThrows(BizException.class, () -> User.reconstitute(1L, username(), CIPHER, "Alice",
                null, null, null, 9, null, null));
    }

    @Test
    @DisplayName("reconstitute：status 为空按停用处理，其余字段原样还原")
    void shouldReconstituteAsDisabledWhenStatusNull() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);

        User user = User.reconstitute(7L, username(), CIPHER, "Alice", "138", "a@b.com", 2L,
                null, created, created);

        assertEquals(7L, user.getId());
        assertEquals(2L, user.getDeptId());
        assertEquals(User.STATUS_DISABLED, user.getStatus());
        assertFalse(user.isEnabled());
        assertEquals(created, user.getCreateTime());
    }

    @Test
    @DisplayName("状态流转：disable 与 enable 互斥切换")
    void shouldToggleStatus() {
        User user = User.create(username(), CIPHER, "Alice", null, null, null, 1);

        user.disable();
        assertEquals(User.STATUS_DISABLED, user.getStatus());
        assertFalse(user.isEnabled());

        user.enable();
        assertEquals(User.STATUS_ENABLED, user.getStatus());
        assertTrue(user.isEnabled());
    }

    @Test
    @DisplayName("口令以值对象承载且 toString 为掩码，不外泄密文")
    void shouldMaskPassword() {
        User user = User.create(username(), CIPHER, "Alice", null, null, null, 1);

        assertEquals("EncryptedPassword(***)", user.getPassword().toString());
        assertFalse(user.getPassword().toString().contains("$2a$"));
    }
}

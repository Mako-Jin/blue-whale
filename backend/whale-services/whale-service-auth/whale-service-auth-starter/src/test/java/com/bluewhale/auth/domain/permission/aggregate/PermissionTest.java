package com.bluewhale.auth.domain.permission.aggregate;

import com.bluewhale.auth.domain.model.vo.PermCode;
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
 * {@link Permission} 聚合不变量与行为单元测试（REQ-ARCH-001 / T-DDD-6）。
 *
 * @author BlueWhale
 */
class PermissionTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final PermCode CODE = PermCode.of("sys:user:list");

    @Test
    @DisplayName("create：合法构造，默认父节点 0、排序 0、状态启用并回填各字段")
    void shouldCreateWithDefaults() {
        Permission permission = Permission.create(null, CODE, "用户列表", "API", "/api/user", "GET", null, null);

        assertNull(permission.getId());
        assertEquals(Permission.ROOT_PARENT_ID, permission.getParentId());
        assertEquals("sys:user:list", permission.getPermCode().value());
        assertEquals("用户列表", permission.getPermName());
        assertEquals("API", permission.getPermType());
        assertEquals("/api/user", permission.getPath());
        assertEquals("GET", permission.getMethod());
        assertEquals(0, permission.getSortNo());
        assertEquals(Permission.STATUS_ENABLED, permission.getStatus());
        assertTrue(permission.isEnabled());
    }

    @Test
    @DisplayName("create：显式父节点、排序与停用状态生效")
    void shouldCreateWithExplicitValues() {
        Permission permission = Permission.create(3L, CODE, "用户列表", "BUTTON", null, null, 9, 0);

        assertEquals(3L, permission.getParentId());
        assertEquals(9, permission.getSortNo());
        assertEquals(Permission.STATUS_DISABLED, permission.getStatus());
        assertFalse(permission.isEnabled());
    }

    @Test
    @DisplayName("不变量①：权限码为空抛参数错误")
    void shouldRejectNullPermCode() {
        BizException ex = assertThrows(BizException.class,
                () -> Permission.create(0L, null, "用户列表", "API", null, null, null, null));
        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("不变量②：权限名称为空抛参数错误")
    void shouldRejectBlankPermName() {
        assertThrows(BizException.class,
                () -> Permission.create(0L, CODE, " ", "API", null, null, null, null));
    }

    @Test
    @DisplayName("不变量③：权限类型非法抛参数错误")
    void shouldRejectIllegalPermType() {
        assertThrows(BizException.class,
                () -> Permission.create(0L, CODE, "用户列表", "UNKNOWN", null, null, null, null));
    }

    @Test
    @DisplayName("不变量④：状态越界抛参数错误")
    void shouldRejectIllegalStatus() {
        assertThrows(BizException.class,
                () -> Permission.create(0L, CODE, "用户列表", "API", null, null, null, 2));
    }

    @Test
    @DisplayName("reconstitute：父节点为空归一化为顶级，其余字段原样还原")
    void shouldReconstituteWithNormalizedParent() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);

        Permission permission = Permission.reconstitute(7L, null, CODE, "用户列表", "API", null, null,
                null, null, created, created);

        assertEquals(7L, permission.getId());
        assertEquals(Permission.ROOT_PARENT_ID, permission.getParentId());
        assertEquals(0, permission.getSortNo());
        assertEquals(created, permission.getCreateTime());
    }

    @Test
    @DisplayName("行为：moveTo/changeCode/rename/changeType 各自生效")
    void shouldApplyProfileChanges() {
        Permission permission = Permission.create(0L, CODE, "旧名", "API", null, null, 1, 1);

        permission.moveTo(3L);
        permission.changeCode(PermCode.of("sys:user:add"));
        permission.rename("新名");
        permission.changeType("BUTTON");

        assertEquals(3L, permission.getParentId());
        assertEquals("sys:user:add", permission.getPermCode().value());
        assertEquals("新名", permission.getPermName());
        assertEquals("BUTTON", permission.getPermType());
    }

    @Test
    @DisplayName("行为：changeApi/changeSort/changeStatus 各自生效")
    void shouldApplyApiAndStatusChanges() {
        Permission permission = Permission.create(0L, CODE, "用户列表", "API", null, null, 1, 1);

        permission.changeApi("/api/user/page", "POST");
        permission.changeSort(5);
        permission.changeStatus(Permission.STATUS_DISABLED);

        assertEquals("/api/user/page", permission.getPath());
        assertEquals("POST", permission.getMethod());
        assertEquals(5, permission.getSortNo());
        assertEquals(Permission.STATUS_DISABLED, permission.getStatus());
    }

    @Test
    @DisplayName("行为：非法入参（moveTo null、changeCode null、rename 空、类型/状态越界）抛参数错误")
    void shouldRejectIllegalChanges() {
        Permission permission = Permission.create(0L, CODE, "用户列表", "API", null, null, 1, 1);

        assertThrows(BizException.class, () -> permission.moveTo(null));
        assertThrows(BizException.class, () -> permission.changeCode(null));
        assertThrows(BizException.class, () -> permission.rename(""));
        assertThrows(BizException.class, () -> permission.changeType("UNKNOWN"));
        assertThrows(BizException.class, () -> permission.changeStatus(9));
    }
}

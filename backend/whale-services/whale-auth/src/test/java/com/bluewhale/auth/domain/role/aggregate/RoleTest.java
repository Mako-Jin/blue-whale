package com.bluewhale.auth.domain.role.aggregate;

import com.bluewhale.auth.domain.model.vo.RoleCode;
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
 * {@link Role} 聚合不变量与行为单元测试（REQ-ARCH-001 / T-DDD-5）。
 *
 * @author BlueWhale
 */
class RoleTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final RoleCode CODE = RoleCode.of("test_role");

    @Test
    @DisplayName("create：合法构造，默认排序 0、默认启用并回填各字段")
    void shouldCreateEnabledRoleByDefault() {
        Role role = Role.create(CODE, "测试角色", "描述", null, null);

        assertNull(role.getId());
        assertEquals("test_role", role.getRoleCode().value());
        assertEquals("测试角色", role.getRoleName());
        assertEquals("描述", role.getDescription());
        assertEquals(0, role.getSortNo());
        assertEquals(Role.STATUS_ENABLED, role.getStatus());
        assertTrue(role.isEnabled());
    }

    @Test
    @DisplayName("create：显式停用状态与排序号生效")
    void shouldCreateDisabledRoleWhenStatusZero() {
        Role role = Role.create(CODE, "测试角色", null, 9, 0);

        assertEquals(9, role.getSortNo());
        assertEquals(Role.STATUS_DISABLED, role.getStatus());
        assertFalse(role.isEnabled());
    }

    @Test
    @DisplayName("不变量①：角色编码为空抛参数错误")
    void shouldRejectNullRoleCode() {
        BizException ex = assertThrows(BizException.class,
                () -> Role.create(null, "测试角色", null, null, null));
        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("不变量②：角色名称为空抛参数错误")
    void shouldRejectBlankRoleName() {
        BizException ex = assertThrows(BizException.class,
                () -> Role.create(CODE, "  ", null, null, null));
        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("不变量③：reconstitute 非法状态抛参数错误")
    void shouldRejectIllegalStatus() {
        assertThrows(BizException.class, () -> Role.reconstitute(1L, CODE, "测试角色", null, 1, 9, null, null));
    }

    @Test
    @DisplayName("reconstitute：status 为空按启用归一化，其余字段原样还原")
    void shouldReconstituteWithDefaultStatusWhenNull() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);

        Role role = Role.reconstitute(7L, CODE, "测试角色", "描述", null, null, created, created);

        assertEquals(7L, role.getId());
        assertEquals("描述", role.getDescription());
        assertEquals(0, role.getSortNo());
        assertEquals(Role.STATUS_ENABLED, role.getStatus());
        assertEquals(created, role.getCreateTime());
    }

    @Test
    @DisplayName("行为：rename/changeDescription/changeSortNo 各自生效")
    void shouldApplyProfileChanges() {
        Role role = Role.create(CODE, "旧名", "旧描述", 1, 1);

        role.rename("新名");
        role.changeDescription("新描述");
        role.changeSortNo(5);

        assertEquals("新名", role.getRoleName());
        assertEquals("新描述", role.getDescription());
        assertEquals(5, role.getSortNo());
    }

    @Test
    @DisplayName("行为：rename 空名称抛参数错误")
    void shouldRejectRenameWithBlankName() {
        Role role = Role.create(CODE, "旧名", null, 1, 1);

        assertThrows(BizException.class, () -> role.rename(""));
    }

    @Test
    @DisplayName("行为：disable/enable/changeStatus 状态流转")
    void shouldToggleStatus() {
        Role role = Role.create(CODE, "测试角色", null, 1, 1);

        role.disable();
        assertEquals(Role.STATUS_DISABLED, role.getStatus());
        assertFalse(role.isEnabled());

        role.enable();
        assertEquals(Role.STATUS_ENABLED, role.getStatus());
        assertTrue(role.isEnabled());

        assertThrows(BizException.class, () -> role.changeStatus(2));
    }
}

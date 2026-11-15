package com.bluewhale.form.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormFieldPermission} 字段级角色权限判定单元测试。
 *
 * @author BlueWhale
 */
class FormFieldPermissionTest {

    @AfterEach
    void clear() {
        FormRoleContext.clear();
    }

    /**
     * 构造字段桩对象
     *
     * @param visibleRoles 可见角色
     * @param editableRoles 可编辑角色
     * @return 字段定义
     */
    private static FormField field(List<String> visibleRoles, List<String> editableRoles) {
        FormField field = new FormField("name", "姓名", "text", false, 50);
        field.setVisibleRoles(visibleRoles);
        field.setEditableRoles(editableRoles);
        return field;
    }

    @Test
    @DisplayName("未配置角色：对所有角色可见可编辑")
    void shouldAllowWhenNoRolesConfigured() {
        FormField field = field(null, null);
        FormRoleContext.set(Set.of("hr"));

        assertTrue(FormFieldPermission.visible(field));
        assertTrue(FormFieldPermission.editable(field));
    }

    @Test
    @DisplayName("未携带角色（内网调用）：不做限制")
    void shouldAllowWhenNoCurrentRoles() {
        FormField field = field(List.of("admin"), List.of("admin"));

        assertTrue(FormFieldPermission.visible(field));
        assertTrue(FormFieldPermission.editable(field));
    }

    @Test
    @DisplayName("角色命中：可见且可编辑")
    void shouldAllowWhenRoleMatches() {
        FormField field = field(List.of("admin", "hr"), List.of("admin"));
        FormRoleContext.set(Set.of("hr"));

        assertTrue(FormFieldPermission.visible(field));
        assertFalse(FormFieldPermission.editable(field));
    }

    @Test
    @DisplayName("角色不命中：不可见且不可编辑")
    void shouldDenyWhenRoleMisses() {
        FormField field = field(List.of("admin"), null);
        FormRoleContext.set(Set.of("guest"));

        assertFalse(FormFieldPermission.visible(field));
        assertTrue(FormFieldPermission.editable(field));
    }
}

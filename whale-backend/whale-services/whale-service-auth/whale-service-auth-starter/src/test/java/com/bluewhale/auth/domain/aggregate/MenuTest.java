package com.bluewhale.auth.domain.aggregate;

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
 * {@link Menu} 聚合不变量与行为单元测试（REQ-ARCH-001 / T-DDD-6）。
 *
 * @author BlueWhale
 */
class MenuTest {

    private static final int PARAM_ERROR_CODE = 400;

    @Test
    @DisplayName("create：合法构造，空值取默认（父节点 0、排序 0、MENU、显示、启用）")
    void shouldCreateWithDefaults() {
        Menu menu = Menu.create(1L, null, "用户管理", "/user", "system/user", "user", null, null, null, null, null);

        assertNull(menu.getId());
        assertEquals(Menu.ROOT_PARENT_ID, menu.getParentId());
        assertEquals("用户管理", menu.getName());
        assertEquals(0, menu.getSort());
        assertEquals(Menu.MENU_TYPE_MENU, menu.getMenuType());
        assertEquals(Menu.VISIBLE_SHOWN, menu.getVisible());
        assertEquals(Menu.STATUS_ENABLED, menu.getStatus());
        assertFalse(menu.isDirectory());
    }

    @Test
    @DisplayName("create：显式目录类型与隐藏停用生效")
    void shouldCreateWithExplicitValues() {
        Menu menu = Menu.create(1L, 3L, "系统管理", null, null, null, 9, Menu.MENU_TYPE_DIR,
                null, Menu.VISIBLE_HIDDEN, Menu.STATUS_DISABLED);

        assertEquals(3L, menu.getParentId());
        assertEquals(9, menu.getSort());
        assertEquals(Menu.MENU_TYPE_DIR, menu.getMenuType());
        assertEquals(Menu.VISIBLE_HIDDEN, menu.getVisible());
        assertEquals(Menu.STATUS_DISABLED, menu.getStatus());
        assertTrue(menu.isDirectory());
    }

    @Test
    @DisplayName("不变量①：菜单名称为空抛参数错误")
    void shouldRejectBlankName() {
        BizException ex = assertThrows(BizException.class,
                () -> Menu.create(1L, 0L, " ", null, null, null, null, null, null, null, null));
        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("不变量②：菜单类型非法抛参数错误")
    void shouldRejectIllegalMenuType() {
        assertThrows(BizException.class,
                () -> Menu.create(1L, 0L, "用户管理", null, null, null, null, "PAGE", null, null, null));
    }

    @Test
    @DisplayName("不变量③：可见性与状态越界抛参数错误")
    void shouldRejectIllegalVisibleAndStatus() {
        assertThrows(BizException.class,
                () -> Menu.create(1L, 0L, "用户管理", null, null, null, null, null, null, 2, null));
        assertThrows(BizException.class,
                () -> Menu.create(1L, 0L, "用户管理", null, null, null, null, null, null, null, 5));
    }

    @Test
    @DisplayName("reconstitute：父节点为空归一化为顶级，其余字段原样还原")
    void shouldReconstituteWithNormalizedParent() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);

        Menu menu = Menu.reconstitute(7L, 1L, null, "用户管理", "/user", "system/user", "user", null,
                null, "sys:user:list", null, null, created, created);

        assertEquals(7L, menu.getId());
        assertEquals(Menu.ROOT_PARENT_ID, menu.getParentId());
        assertEquals("sys:user:list", menu.getPermissionCode());
        assertEquals(created, menu.getCreateTime());
    }

    @Test
    @DisplayName("行为：moveTo/rename/changeRoute/changeSort 各自生效")
    void shouldApplyProfileChanges() {
        Menu menu = Menu.create(1L, 0L, "旧名", "/old", "old", "old", 1, Menu.MENU_TYPE_MENU, null, 1, 1);

        menu.moveTo(3L);
        menu.rename("新名");
        menu.changeRoute("/new", "system/new", "new");
        menu.changeSort(5);

        assertEquals(3L, menu.getParentId());
        assertEquals("新名", menu.getName());
        assertEquals("/new", menu.getPath());
        assertEquals("system/new", menu.getComponent());
        assertEquals("new", menu.getIcon());
        assertEquals(5, menu.getSort());
    }

    @Test
    @DisplayName("行为：changeMenuType/bindPermissionCode/changeVisible/changeStatus 各自生效")
    void shouldApplyAttributeChanges() {
        Menu menu = Menu.create(1L, 0L, "用户管理", null, null, null, 1, Menu.MENU_TYPE_MENU, null, 1, 1);

        menu.changeMenuType(Menu.MENU_TYPE_DIR);
        menu.bindPermissionCode("sys:user:list");
        menu.changeVisible(Menu.VISIBLE_HIDDEN);
        menu.changeStatus(Menu.STATUS_DISABLED);

        assertEquals(Menu.MENU_TYPE_DIR, menu.getMenuType());
        assertEquals("sys:user:list", menu.getPermissionCode());
        assertEquals(Menu.VISIBLE_HIDDEN, menu.getVisible());
        assertEquals(Menu.STATUS_DISABLED, menu.getStatus());
    }

    @Test
    @DisplayName("行为：非法入参（rename 空、moveTo null、类型/可见性/状态越界）抛参数错误")
    void shouldRejectIllegalChanges() {
        Menu menu = Menu.create(1L, 0L, "用户管理", null, null, null, 1, Menu.MENU_TYPE_MENU, null, 1, 1);

        assertThrows(BizException.class, () -> menu.rename(""));
        assertThrows(BizException.class, () -> menu.moveTo(null));
        assertThrows(BizException.class, () -> menu.changeMenuType("PAGE"));
        assertThrows(BizException.class, () -> menu.changeVisible(2));
        assertThrows(BizException.class, () -> menu.changeStatus(3));
    }
}

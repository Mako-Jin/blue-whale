package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.MenuAggregate;
import com.bluewhale.auth.infrastructure.persistence.po.SysMenuPo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MenuPersistenceAssembler} 逐字段比对单元测试（REQ-ARCH-001 / T-DDD-6）：
 * 防止 PO ↔ 领域对象转换漏字段。
 *
 * @author BlueWhale
 */
class MenuPersistenceAssemblerTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 9, 0, 0);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 1, 2, 9, 0, 0);

    private static SysMenuPo fullPo() {
        SysMenuPo po = new SysMenuPo();
        po.setId(5L);
        po.setTenantId(1L);
        po.setParentId(3L);
        po.setName("用户管理");
        po.setPath("/user");
        po.setComponent("system/user");
        po.setIcon("user");
        po.setSort(2);
        po.setMenuType(MenuAggregate.MENU_TYPE_MENU);
        po.setPermissionCode("sys:user:list");
        po.setVisible(MenuAggregate.VISIBLE_SHOWN);
        po.setStatus(MenuAggregate.STATUS_ENABLED);
        po.setDeleted(0);
        po.setCreateTime(CREATED);
        po.setUpdateTime(UPDATED);
        return po;
    }

    private static MenuAggregate fullDomain() {
        return MenuAggregate.reconstitute(5L, 1L, 3L, "用户管理", "/user", "system/user", "user", 2,
                MenuAggregate.MENU_TYPE_MENU, "sys:user:list", MenuAggregate.VISIBLE_SHOWN, MenuAggregate.STATUS_ENABLED, CREATED, UPDATED);
    }

    @Test
    @DisplayName("PO → 领域：Menu 聚合全部关键字段逐项一致")
    void shouldMapPoToDomainFieldByField() {
        MenuAggregate menu = MenuPersistenceAssembler.toDomain(fullPo());

        assertEquals(5L, menu.getId());
        assertEquals(3L, menu.getParentId());
        assertEquals("用户管理", menu.getName());
        assertEquals("/user", menu.getPath());
        assertEquals("system/user", menu.getComponent());
        assertEquals("user", menu.getIcon());
        assertEquals(2, menu.getSort());
        assertEquals(MenuAggregate.MENU_TYPE_MENU, menu.getMenuType());
        assertEquals("sys:user:list", menu.getPermissionCode());
        assertEquals(MenuAggregate.VISIBLE_SHOWN, menu.getVisible());
        assertEquals(MenuAggregate.STATUS_ENABLED, menu.getStatus());
        assertEquals(CREATED, menu.getCreateTime());
        assertEquals(UPDATED, menu.getUpdateTime());
    }

    @Test
    @DisplayName("领域 → PO：SysMenu 各字段逐项一致，未纳入聚合的字段保持为空")
    void shouldMapDomainToPoFieldByField() {
        SysMenuPo po = MenuPersistenceAssembler.toPo(fullDomain());

        assertEquals(5L, po.getId());
        assertEquals(3L, po.getParentId());
        assertEquals("用户管理", po.getName());
        assertEquals("/user", po.getPath());
        assertEquals("system/user", po.getComponent());
        assertEquals("user", po.getIcon());
        assertEquals(2, po.getSort());
        assertEquals(MenuAggregate.MENU_TYPE_MENU, po.getMenuType());
        assertEquals("sys:user:list", po.getPermissionCode());
        assertEquals(MenuAggregate.VISIBLE_SHOWN, po.getVisible());
        assertEquals(MenuAggregate.STATUS_ENABLED, po.getStatus());
        assertEquals(CREATED, po.getCreateTime());
        assertEquals(UPDATED, po.getUpdateTime());
        // deleted 不属本步 Menu 聚合关键项，转换不携带
        assertNull(po.getDeleted());
    }

    @Test
    @DisplayName("PO → 领域：parentId/sort/menuType/visible/status 为空按默认归一化")
    void shouldNormalizeNullFields() {
        SysMenuPo po = fullPo();
        po.setParentId(null);
        po.setSort(null);
        po.setMenuType(null);
        po.setVisible(null);
        po.setStatus(null);

        MenuAggregate menu = MenuPersistenceAssembler.toDomain(po);

        assertEquals(MenuAggregate.ROOT_PARENT_ID, menu.getParentId());
        assertEquals(0, menu.getSort());
        assertEquals(MenuAggregate.MENU_TYPE_MENU, menu.getMenuType());
        assertEquals(MenuAggregate.VISIBLE_SHOWN, menu.getVisible());
        assertEquals(MenuAggregate.STATUS_ENABLED, menu.getStatus());
    }
}

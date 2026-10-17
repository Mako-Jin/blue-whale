package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.auth.entity.SysMenu;
import com.bluewhale.auth.mapper.SysMenuMapper;
import com.bluewhale.auth.vo.MenuVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * {@link SysMenuServiceImpl} 单元测试：当前用户菜单树的权限过滤（AUTH-21）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SysMenuServiceImplTest {

    private static final long ROOT_ID = 1L;
    private static final long USER_MENU_ID = 2L;
    private static final long ROLE_MENU_ID = 3L;
    private static final long ROOT_PARENT_ID = 0L;

    @Mock
    private SysMenuMapper menuMapper;

    private SysMenuServiceImpl menuService;

    @BeforeEach
    void setUp() {
        menuService = new SysMenuServiceImpl();
        ReflectionTestUtils.setField(menuService, "baseMapper", menuMapper);
    }

    private static SysMenu menu(long id, long parentId, String name, String permissionCode) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setPermissionCode(permissionCode);
        menu.setStatus(1);
        menu.setVisible(1);
        return menu;
    }

    private static List<SysMenu> allMenus() {
        return List.of(
                menu(ROOT_ID, ROOT_PARENT_ID, "系统管理", null),
                menu(USER_MENU_ID, ROOT_ID, "用户管理", "sys:user:list"),
                menu(ROLE_MENU_ID, ROOT_ID, "角色管理", "sys:role:list"));
    }

    @Test
    @DisplayName("treeByPermissionCodes：仅保留授权菜单并补齐祖先目录")
    void shouldRetainGrantedMenuWithAncestors() {
        when(menuMapper.selectList(ArgumentMatchers.<Wrapper<SysMenu>>any())).thenReturn(allMenus());

        List<MenuVO> tree = menuService.treeByPermissionCodes(Set.of("sys:user:list"));

        assertEquals(1, tree.size());
        assertEquals("系统管理", tree.get(0).getName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("用户管理", tree.get(0).getChildren().get(0).getName());
    }

    @Test
    @DisplayName("treeByPermissionCodes：权限码为空集合时返回空树")
    void shouldReturnEmptyTreeWhenNoPermission() {
        when(menuMapper.selectList(ArgumentMatchers.<Wrapper<SysMenu>>any())).thenReturn(allMenus());

        List<MenuVO> tree = menuService.treeByPermissionCodes(Set.of());

        assertEquals(0, tree.size());
    }

    @Test
    @DisplayName("treeByPermissionCodes：权限码为 null（超管）时返回全部启用可见菜单")
    void shouldReturnAllMenusForNullCodes() {
        when(menuMapper.selectList(ArgumentMatchers.<Wrapper<SysMenu>>any())).thenReturn(allMenus());

        List<MenuVO> tree = menuService.treeByPermissionCodes(null);

        assertEquals(1, tree.size());
        assertEquals(2, tree.get(0).getChildren().size());
    }
}

package com.bluewhale.auth.application.menu;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.SaveMenuCommand;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.MenuDomainEvent;
import com.bluewhale.auth.domain.menu.aggregate.Menu;
import com.bluewhale.auth.domain.menu.repository.MenuRepository;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.auth.application.model.vo.MenuVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bluewhale.auth.application.service.MenuAppService;
import com.bluewhale.auth.application.service.impl.MenuAppServiceImpl;

/**
 * {@link MenuAppService} 单元测试：菜单树/权限过滤树/详情/增删改（AUTH-17 / T-DDD-9）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MenuAppServiceTest {

    private static final long ROOT_ID = 1L;
    private static final long USER_MENU_ID = 2L;
    private static final long ROLE_MENU_ID = 3L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 9, 0, 0);

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private MenuAppServiceImpl menuAppService;

    @BeforeEach
    void bindTenant() {
        TenantContext.set(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static Menu menu(Long id, Long parentId, String name, String menuType, String permissionCode) {
        return Menu.reconstitute(id, 1L, parentId, name, "/" + id, "component/" + id, "icon", 0, menuType,
                permissionCode, Menu.VISIBLE_SHOWN, Menu.STATUS_ENABLED, NOW, NOW);
    }

    private static List<Menu> allMenus() {
        return List.of(
                menu(ROOT_ID, Menu.ROOT_PARENT_ID, "系统管理", Menu.MENU_TYPE_DIR, null),
                menu(USER_MENU_ID, ROOT_ID, "用户管理", Menu.MENU_TYPE_MENU, "sys:user:list"),
                menu(ROLE_MENU_ID, ROOT_ID, "角色管理", Menu.MENU_TYPE_MENU, "sys:role:list"));
    }

    @Test
    @DisplayName("tree：按父子关系组装两级菜单树")
    void shouldBuildTree() {
        when(menuRepository.findAll()).thenReturn(allMenus());

        List<MenuVO> tree = menuAppService.tree();

        assertEquals(1, tree.size());
        assertEquals("系统管理", tree.get(0).getName());
        assertEquals(2, tree.get(0).getChildren().size());
    }

    @Test
    @DisplayName("treeByPermissionCodes：仅保留授权菜单并补齐祖先目录")
    void shouldRetainGrantedMenuWithAncestors() {
        when(menuRepository.findEnabledVisible()).thenReturn(allMenus());

        List<MenuVO> tree = menuAppService.treeByPermissionCodes(Set.of("sys:user:list"));

        assertEquals(1, tree.size());
        assertEquals("系统管理", tree.get(0).getName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("用户管理", tree.get(0).getChildren().get(0).getName());
    }

    @Test
    @DisplayName("treeByPermissionCodes：未命中任何权限码时返回空树")
    void shouldReturnEmptyTreeWhenNoCodeMatched() {
        when(menuRepository.findEnabledVisible()).thenReturn(allMenus());

        List<MenuVO> tree = menuAppService.treeByPermissionCodes(Set.of("sys:unknown:list"));

        assertEquals(0, tree.size());
    }

    @Test
    @DisplayName("getDetail：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectGetDetailWhenMissing() {
        when(menuRepository.findById(ROOT_ID)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> menuAppService.getDetail(ROOT_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("create：空值默认由聚合适配（父节点 0、排序 0、MENU、显示、启用）")
    void shouldCreateWithDefaults() {
        when(menuRepository.save(any(Menu.class))).thenAnswer(inv -> inv.getArgument(0));

        menuAppService.create(new SaveMenuCommand(null, "用户管理", "/user", "system/user", "user",
                null, null, null, null, null));

        ArgumentCaptor<Menu> captor = ArgumentCaptor.forClass(Menu.class);
        verify(menuRepository).save(captor.capture());
        Menu saved = captor.getValue();
        assertEquals(Menu.ROOT_PARENT_ID, saved.getParentId());
        assertEquals(0, saved.getSort());
        assertEquals(Menu.MENU_TYPE_MENU, saved.getMenuType());
        assertEquals(Menu.VISIBLE_SHOWN, saved.getVisible());
        assertEquals(Menu.STATUS_ENABLED, saved.getStatus());
        verify(eventPublisher).publish(new MenuDomainEvent(null, ChangeType.CREATED));
    }

    @Test
    @DisplayName("update：不存在时抛 BizException(NOT_FOUND) 且不落库")
    void shouldRejectUpdateWhenMissing() {
        when(menuRepository.findById(ROOT_ID)).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> menuAppService.update(ROOT_ID,
                new SaveMenuCommand(null, "新名", null, null, null, null, null, null, null, null)));
        verify(menuRepository, never()).save(any(Menu.class));
    }

    @Test
    @DisplayName("update：命中时应用全部字段并落库")
    void shouldUpdateWhenExists() {
        Menu stored = menu(ROOT_ID, Menu.ROOT_PARENT_ID, "旧名", Menu.MENU_TYPE_MENU, null);
        when(menuRepository.findById(ROOT_ID)).thenReturn(Optional.of(stored));

        menuAppService.update(ROOT_ID, new SaveMenuCommand(ROOT_ID, "新名", "/new", "new/component", "new-icon",
                5, Menu.MENU_TYPE_DIR, "sys:user:list", Menu.VISIBLE_HIDDEN, Menu.STATUS_DISABLED));

        ArgumentCaptor<Menu> captor = ArgumentCaptor.forClass(Menu.class);
        verify(menuRepository).save(captor.capture());
        Menu saved = captor.getValue();
        assertEquals("新名", saved.getName());
        assertEquals("/new", saved.getPath());
        assertEquals(5, saved.getSort());
        assertEquals(Menu.MENU_TYPE_DIR, saved.getMenuType());
        assertEquals("sys:user:list", saved.getPermissionCode());
        assertEquals(Menu.VISIBLE_HIDDEN, saved.getVisible());
        assertEquals(Menu.STATUS_DISABLED, saved.getStatus());
        verify(eventPublisher).publish(new MenuDomainEvent(ROOT_ID, ChangeType.UPDATED));
    }

    @Test
    @DisplayName("delete：存在子菜单时抛 BizException 且不删除")
    void shouldRejectDeleteWhenHasChildren() {
        when(menuRepository.countChildren(ROOT_ID)).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> menuAppService.delete(ROOT_ID));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("存在子菜单，不允许删除", ex.getMessage());
        verify(menuRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("delete：无子菜单时逻辑删除")
    void shouldDeleteWhenNoChildren() {
        when(menuRepository.countChildren(ROOT_ID)).thenReturn(0L);

        menuAppService.delete(ROOT_ID);

        verify(menuRepository).deleteById(ROOT_ID);
        verify(eventPublisher).publish(new MenuDomainEvent(ROOT_ID, ChangeType.DELETED));
    }
}

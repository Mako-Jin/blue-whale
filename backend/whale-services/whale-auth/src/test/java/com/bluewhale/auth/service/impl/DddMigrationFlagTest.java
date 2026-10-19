package com.bluewhale.auth.service.impl;

import com.bluewhale.auth.config.DddMigrationProperties;
import com.bluewhale.auth.domain.dept.aggregate.Dept;
import com.bluewhale.auth.domain.dept.repository.DeptRepository;
import com.bluewhale.auth.domain.menu.aggregate.Menu;
import com.bluewhale.auth.domain.menu.repository.MenuRepository;
import com.bluewhale.auth.domain.model.vo.PermCode;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.permission.aggregate.Permission;
import com.bluewhale.auth.domain.permission.repository.PermissionRepository;
import com.bluewhale.auth.domain.role.aggregate.Role;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.auth.entity.SysDept;
import com.bluewhale.auth.entity.SysMenu;
import com.bluewhale.auth.entity.SysPermission;
import com.bluewhale.auth.entity.SysRole;
import com.bluewhale.auth.mapper.SysDeptMapper;
import com.bluewhale.auth.mapper.SysMenuMapper;
import com.bluewhale.auth.mapper.SysPermissionMapper;
import com.bluewhale.auth.mapper.SysRoleMapper;
import com.bluewhale.auth.mapper.SysRolePermissionMapper;
import com.bluewhale.auth.mapper.SysUserRoleMapper;
import com.bluewhale.auth.security.PermissionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DDD 迁移开关（{@code whale.auth.ddd.*}）读路径双路径单元测试（REQ-ARCH-001 / T-DDD-10）：
 * 开关关闭时读旧 Mapper 且不触达新仓储；开关开启时读新仓储且不触达旧 Mapper。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class DddMigrationFlagTest {

    private static final long ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 9, 0, 0);

    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRolePermissionMapper rolePermissionMapper;
    @Mock
    private SysPermissionMapper permissionMapper;
    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private SysDeptMapper deptMapper;
    @Mock
    private PermissionCache permissionCache;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private DeptRepository deptRepository;

    private DddMigrationProperties properties;
    private SysRoleServiceImpl roleService;
    private SysMenuServiceImpl menuService;
    private SysPermissionServiceImpl permissionService;
    private SysDeptServiceImpl deptService;

    @BeforeEach
    void setUp() {
        properties = new DddMigrationProperties();

        roleService = new SysRoleServiceImpl(userRoleMapper, rolePermissionMapper, permissionMapper, permissionCache);
        ReflectionTestUtils.setField(roleService, "baseMapper", roleMapper);
        ReflectionTestUtils.setField(roleService, "dddProperties", properties);
        ReflectionTestUtils.setField(roleService, "roleRepository", roleRepository);

        menuService = new SysMenuServiceImpl();
        ReflectionTestUtils.setField(menuService, "baseMapper", menuMapper);
        ReflectionTestUtils.setField(menuService, "dddProperties", properties);
        ReflectionTestUtils.setField(menuService, "menuRepository", menuRepository);

        permissionService = new SysPermissionServiceImpl(permissionCache);
        ReflectionTestUtils.setField(permissionService, "baseMapper", permissionMapper);
        ReflectionTestUtils.setField(permissionService, "dddProperties", properties);
        ReflectionTestUtils.setField(permissionService, "permissionRepository", permissionRepository);

        deptService = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(deptService, "baseMapper", deptMapper);
        ReflectionTestUtils.setField(deptService, "dddProperties", properties);
        ReflectionTestUtils.setField(deptService, "deptRepository", deptRepository);
    }

    private static SysRole rolePo() {
        SysRole po = new SysRole();
        po.setId(ID);
        po.setRoleCode("test_role");
        po.setRoleName("测试角色");
        po.setSortNo(1);
        po.setStatus(Role.STATUS_ENABLED);
        po.setCreateTime(NOW);
        po.setUpdateTime(NOW);
        return po;
    }

    private static Role roleDomain() {
        return Role.reconstitute(ID, RoleCode.of("test_role"), "测试角色", null, 1,
                Role.STATUS_ENABLED, NOW, NOW);
    }

    private static SysMenu menuPo() {
        SysMenu po = new SysMenu();
        po.setId(ID);
        po.setParentId(0L);
        po.setName("用户管理");
        po.setMenuType(Menu.MENU_TYPE_MENU);
        po.setVisible(Menu.VISIBLE_SHOWN);
        po.setStatus(Menu.STATUS_ENABLED);
        return po;
    }

    private static Menu menuDomain() {
        return Menu.reconstitute(ID, 0L, "用户管理", null, null, null, 0,
                Menu.MENU_TYPE_MENU, null, Menu.VISIBLE_SHOWN, Menu.STATUS_ENABLED, NOW, NOW);
    }

    private static SysPermission permissionPo() {
        SysPermission po = new SysPermission();
        po.setId(ID);
        po.setParentId(0L);
        po.setPermCode("sys:user:list");
        po.setPermName("用户列表");
        po.setPermType("API");
        po.setSortNo(1);
        po.setStatus(Permission.STATUS_ENABLED);
        return po;
    }

    private static Permission permissionDomain() {
        return Permission.reconstitute(ID, 0L, PermCode.of("sys:user:list"), "用户列表", "API",
                null, null, 1, Permission.STATUS_ENABLED, NOW, NOW);
    }

    private static SysDept deptPo() {
        SysDept po = new SysDept();
        po.setId(ID);
        po.setParentId(0L);
        po.setDeptName("研发部");
        po.setSort(1);
        return po;
    }

    private static Dept deptDomain() {
        return Dept.reconstitute(ID, 0L, "研发部", 1, null, NOW, NOW);
    }

    @Test
    @DisplayName("Role flag=false：getDetail 走旧 Mapper，不触达新仓储")
    void shouldUseOldMapperForRoleWhenFlagOff() {
        when(roleMapper.selectById(ID)).thenReturn(rolePo());

        roleService.getDetail(ID);

        verify(roleMapper).selectById(ID);
        verify(roleRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Role flag=true：getDetail 走新仓储，不触达旧 Mapper")
    void shouldUseRepositoryForRoleWhenFlagOn() {
        properties.setRole(true);
        when(roleRepository.findById(ID)).thenReturn(Optional.of(roleDomain()));

        roleService.getDetail(ID);

        verify(roleRepository).findById(ID);
        verify(roleMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("Menu flag=false：getDetail 走旧 Mapper，不触达新仓储")
    void shouldUseOldMapperForMenuWhenFlagOff() {
        when(menuMapper.selectById(ID)).thenReturn(menuPo());

        menuService.getDetail(ID);

        verify(menuMapper).selectById(ID);
        verify(menuRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Menu flag=true：getDetail 走新仓储，不触达旧 Mapper")
    void shouldUseRepositoryForMenuWhenFlagOn() {
        properties.setMenu(true);
        when(menuRepository.findById(ID)).thenReturn(Optional.of(menuDomain()));

        menuService.getDetail(ID);

        verify(menuRepository).findById(ID);
        verify(menuMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("Permission flag=false：getDetail 走旧 Mapper，不触达新仓储")
    void shouldUseOldMapperForPermissionWhenFlagOff() {
        when(permissionMapper.selectById(ID)).thenReturn(permissionPo());

        permissionService.getDetail(ID);

        verify(permissionMapper).selectById(ID);
        verify(permissionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Permission flag=true：getDetail 走新仓储，不触达旧 Mapper")
    void shouldUseRepositoryForPermissionWhenFlagOn() {
        properties.setPermission(true);
        when(permissionRepository.findById(ID)).thenReturn(Optional.of(permissionDomain()));

        permissionService.getDetail(ID);

        verify(permissionRepository).findById(ID);
        verify(permissionMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("Dept flag=false：getDetail 走旧 Mapper，不触达新仓储")
    void shouldUseOldMapperForDeptWhenFlagOff() {
        when(deptMapper.selectById(ID)).thenReturn(deptPo());

        deptService.getDetail(ID);

        verify(deptMapper).selectById(ID);
        verify(deptRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Dept flag=true：getDetail 走新仓储，不触达旧 Mapper")
    void shouldUseRepositoryForDeptWhenFlagOn() {
        properties.setDept(true);
        when(deptRepository.findById(ID)).thenReturn(Optional.of(deptDomain()));

        deptService.getDetail(ID);

        verify(deptRepository).findById(ID);
        verify(deptMapper, never()).selectById(any());
    }
}

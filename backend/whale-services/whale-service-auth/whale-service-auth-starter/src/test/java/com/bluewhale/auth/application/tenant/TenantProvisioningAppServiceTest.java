package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.application.tenant.command.CreateTenantCommand;
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
import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.auth.vo.TenantVO;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TenantProvisioningAppService} 单元测试（Wave 5.54 Phase 1）：租户开通编排与准入校验。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TenantProvisioningAppServiceTest {

    private static final long NEW_TENANT_ID = 100L;
    private static final long ROOT_DEPT_ID = 200L;
    private static final long ROLE_ID = 300L;
    private static final long ADMIN_ID = 400L;
    private static final String CIPHER = "$2a$10$abcdefghijklmnopqrstuv";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private TenantAppService tenantAppService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private DeptRepository deptRepository;

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TenantStatusCache tenantStatusCache;

    @Mock
    private TenantMembershipAppService tenantMembershipAppService;

    @InjectMocks
    private TenantProvisioningAppService service;

    @Test
    @DisplayName("开通：创建租户、部门根、菜单、默认角色与管理员，且不下发平台专属权限")
    void shouldProvisionTenantWithDefaults() {
        AtomicLong menuIdSequence = new AtomicLong(10_000L);
        when(tenantAppService.requirePlatformTenant()).thenReturn(platformTenant());
        when(tenantRepository.findByCode("acme")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant draft = invocation.getArgument(0);
            return Tenant.reconstitute(NEW_TENANT_ID, draft.getTenantCode(), draft.getTenantName(), "TENANT",
                    draft.getStatus(), draft.getExpireTime(), draft.getQuotaJson(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(deptRepository.save(any(Dept.class))).thenAnswer(invocation -> {
            Dept draft = invocation.getArgument(0);
            return Dept.reconstitute(ROOT_DEPT_ID, draft.getTenantId(), draft.getParentId(), draft.getDeptName(),
                    draft.getSort(), draft.getLeader(), draft.getCreateTime(), draft.getUpdateTime());
        });
        when(menuRepository.findAllByTenantId(Tenant.PLATFORM_TENANT_ID))
                .thenReturn(List.of(platformMenu(10L, 0L, "系统管理", "DIR", null),
                        platformMenu(11L, 10L, "租户管理", "MENU", "sys:tenant:list")));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu draft = invocation.getArgument(0);
            long id = menuIdSequence.incrementAndGet();
            return Menu.reconstitute(id, draft.getTenantId(), draft.getParentId(), draft.getName(), draft.getPath(),
                    draft.getComponent(), draft.getIcon(), draft.getSort(), draft.getMenuType(),
                    draft.getPermissionCode(), draft.getVisible(), draft.getStatus(), NOW, NOW);
        });
        when(permissionRepository.findEnabledAll()).thenReturn(List.of(
                permission(500L, "sys:tenant:list"), permission(501L, "sys:user:list")));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role draft = invocation.getArgument(0);
            return Role.reconstitute(ROLE_ID, draft.getTenantId(), draft.getRoleCode(), draft.getRoleName(),
                    draft.getDescription(), draft.getSortNo(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User draft = invocation.getArgument(0);
            return User.reconstitute(ADMIN_ID, draft.getTenantId(), draft.getUsername(), draft.getPassword(),
                    draft.getNickname(), draft.getPhone(), draft.getEmail(), draft.getDeptId(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(passwordEncoder.encode("initPwd")).thenReturn(CIPHER);

        TenantVO result = service.provision(new CreateTenantCommand("acme", "ACME",
                LocalDateTime.now().plusDays(30), "{\"maxUser\":10}", "admin", "initPwd", "管理员"));

        ArgumentCaptor<Dept> deptCaptor = ArgumentCaptor.forClass(Dept.class);
        verify(deptRepository, times(1)).save(deptCaptor.capture());
        assertEquals(Long.valueOf(NEW_TENANT_ID), deptCaptor.getValue().getTenantId());
        assertEquals("ACME", deptCaptor.getValue().getDeptName());

        verify(menuRepository, times(2)).save(any(Menu.class));

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());
        assertEquals("tenant_admin", roleCaptor.getValue().getRoleCode().value());
        assertEquals(Long.valueOf(NEW_TENANT_ID), roleCaptor.getValue().getTenantId());
        verify(roleRepository).replacePermissions(eq(ROLE_ID), eq(List.of(501L)));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(Long.valueOf(NEW_TENANT_ID), userCaptor.getValue().getTenantId());
        assertEquals(Long.valueOf(ROOT_DEPT_ID), userCaptor.getValue().getDeptId());
        verify(userRepository).replaceRoles(ADMIN_ID, List.of(ROLE_ID));

        assertEquals("acme", result.getTenantCode());
        assertEquals(Long.valueOf(NEW_TENANT_ID), result.getId());
    }

    @Test
    @DisplayName("开通：按平台业务角色模板下发同名角色并复制模板权限集（剔除平台专属与停用权限）")
    void shouldCopyBusinessRoleTemplates() {
        AtomicLong roleIdSequence = new AtomicLong(300L);
        when(tenantAppService.requirePlatformTenant()).thenReturn(platformTenant());
        when(tenantRepository.findByCode("acme")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant draft = invocation.getArgument(0);
            return Tenant.reconstitute(NEW_TENANT_ID, draft.getTenantCode(), draft.getTenantName(), "TENANT",
                    draft.getStatus(), draft.getExpireTime(), draft.getQuotaJson(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(deptRepository.save(any(Dept.class))).thenAnswer(invocation -> {
            Dept draft = invocation.getArgument(0);
            return Dept.reconstitute(ROOT_DEPT_ID, draft.getTenantId(), draft.getParentId(), draft.getDeptName(),
                    draft.getSort(), draft.getLeader(), draft.getCreateTime(), draft.getUpdateTime());
        });
        when(permissionRepository.findEnabledAll()).thenReturn(List.of(
                permission(140L, "sys:archive:category:list"),
                permission(44L, "sys:workflow:instance:start"),
                permission(165L, "sys:tenant:list")));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role draft = invocation.getArgument(0);
            return Role.reconstitute(roleIdSequence.incrementAndGet(), draft.getTenantId(), draft.getRoleCode(),
                    draft.getRoleName(), draft.getDescription(), draft.getSortNo(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        // 仅 archive_manager 模板存在，其余 12 个编码按缺失跳过
        when(roleRepository.findByTenantIdAndCode(Tenant.PLATFORM_TENANT_ID, "archive_manager"))
                .thenReturn(Optional.of(Role.reconstitute(9_000L, Tenant.PLATFORM_TENANT_ID,
                        RoleCode.of("archive_manager"), "档案管理员", "档案全量", 140, Role.STATUS_ENABLED, NOW, NOW)));
        // 模板授权含可下发 140、平台专属 165 与不存在的 999
        when(roleRepository.listPermissionIds(9_000L)).thenReturn(List.of(140L, 165L, 999L));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User draft = invocation.getArgument(0);
            return User.reconstitute(ADMIN_ID, draft.getTenantId(), draft.getUsername(), draft.getPassword(),
                    draft.getNickname(), draft.getPhone(), draft.getEmail(), draft.getDeptId(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(passwordEncoder.encode("initPwd")).thenReturn(CIPHER);

        service.provision(new CreateTenantCommand("acme", "ACME", null, null, "admin", "initPwd", "管理员"));

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(2)).save(roleCaptor.capture());
        List<Role> saved = roleCaptor.getAllValues();
        assertEquals("tenant_admin", saved.get(0).getRoleCode().value());
        Role copied = saved.get(1);
        assertEquals("archive_manager", copied.getRoleCode().value());
        assertEquals("档案管理员", copied.getRoleName());
        assertEquals("档案全量", copied.getDescription());
        assertEquals(140, copied.getSortNo());
        assertEquals(Long.valueOf(NEW_TENANT_ID), copied.getTenantId());
        // 管理员：140 + 44（165 为平台专属被剔除）；模板角色：仅 140（165 平台专属、999 非启用权限被剔除）
        verify(roleRepository).replacePermissions(301L, List.of(140L, 44L));
        verify(roleRepository).replacePermissions(302L, List.of(140L));
    }

    @Test
    @DisplayName("开通：子菜单先于父菜单返回时不报错，按轮次解析父子层级")
    void shouldCopyMenusWhenChildListedBeforeParent() {
        AtomicLong menuIdSequence = new AtomicLong(20_000L);
        when(tenantAppService.requirePlatformTenant()).thenReturn(platformTenant());
        when(tenantRepository.findByCode("acme")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant draft = invocation.getArgument(0);
            return Tenant.reconstitute(NEW_TENANT_ID, draft.getTenantCode(), draft.getTenantName(), "TENANT",
                    draft.getStatus(), draft.getExpireTime(), draft.getQuotaJson(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(deptRepository.save(any(Dept.class))).thenAnswer(invocation -> {
            Dept draft = invocation.getArgument(0);
            return Dept.reconstitute(ROOT_DEPT_ID, draft.getTenantId(), draft.getParentId(), draft.getDeptName(),
                    draft.getSort(), draft.getLeader(), draft.getCreateTime(), draft.getUpdateTime());
        });
        // 故意把子菜单排在父菜单之前，模拟无序遍历
        when(menuRepository.findAllByTenantId(Tenant.PLATFORM_TENANT_ID))
                .thenReturn(List.of(platformMenu(11L, 10L, "租户管理", "MENU", null),
                        platformMenu(10L, 0L, "系统管理", "DIR", null)));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu draft = invocation.getArgument(0);
            long id = menuIdSequence.incrementAndGet();
            return Menu.reconstitute(id, draft.getTenantId(), draft.getParentId(), draft.getName(), draft.getPath(),
                    draft.getComponent(), draft.getIcon(), draft.getSort(), draft.getMenuType(),
                    draft.getPermissionCode(), draft.getVisible(), draft.getStatus(), NOW, NOW);
        });
        when(permissionRepository.findEnabledAll()).thenReturn(List.of());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role draft = invocation.getArgument(0);
            return Role.reconstitute(ROLE_ID, draft.getTenantId(), draft.getRoleCode(), draft.getRoleName(),
                    draft.getDescription(), draft.getSortNo(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User draft = invocation.getArgument(0);
            return User.reconstitute(ADMIN_ID, draft.getTenantId(), draft.getUsername(), draft.getPassword(),
                    draft.getNickname(), draft.getPhone(), draft.getEmail(), draft.getDeptId(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(passwordEncoder.encode("initPwd")).thenReturn(CIPHER);

        service.provision(new CreateTenantCommand("acme", "ACME", null, null, "admin", "initPwd", "管理员"));

        ArgumentCaptor<Menu> menuCaptor = ArgumentCaptor.forClass(Menu.class);
        verify(menuRepository, times(2)).save(menuCaptor.capture());
        List<Menu> saved = menuCaptor.getAllValues();
        assertEquals("系统管理", saved.get(0).getName());
        assertEquals(Long.valueOf(Menu.ROOT_PARENT_ID), saved.get(0).getParentId());
        assertEquals("租户管理", saved.get(1).getName());
        // 子菜单在第二轮落库，父 ID 指向第一轮保存父菜单时返回的 20001
        assertEquals(Long.valueOf(20_001L), saved.get(1).getParentId());
    }

    @Test
    @DisplayName("开通：租户编码已存在时快速失败且不落库")
    void shouldRejectDuplicateTenantCode() {
        when(tenantAppService.requirePlatformTenant()).thenReturn(platformTenant());
        when(tenantRepository.findByCode("acme"))
                .thenReturn(Optional.of(Tenant.reconstitute(NEW_TENANT_ID, "acme", "ACME", "TENANT", 1,
                        null, null, NOW, NOW)));

        BizException ex = assertThrows(BizException.class, () -> service.provision(
                new CreateTenantCommand("acme", "ACME", null, null, "admin", "initPwd", "管理员")));

        assertTrue(ex.getMessage().contains("租户编码已存在"));
        verify(tenantRepository, never()).save(any(Tenant.class));
        verifyNoInteractions(deptRepository, menuRepository, roleRepository, userRepository,
                permissionRepository, passwordEncoder);
    }

    @Test
    @DisplayName("开通：非平台租户调用时 403 且不创建任何数据")
    void shouldRejectNonPlatformTenant() {
        when(tenantAppService.requirePlatformTenant())
                .thenThrow(new BizException(403, "仅平台租户可管理租户"));

        BizException ex = assertThrows(BizException.class, () -> service.provision(
                new CreateTenantCommand("acme", "ACME", null, null, "admin", "initPwd", "管理员")));

        assertEquals(403, ex.getCode());
        verifyNoInteractions(tenantRepository, deptRepository, menuRepository, roleRepository, userRepository,
                permissionRepository, passwordEncoder);
    }

    private static Tenant platformTenant() {
        return Tenant.reconstitute(Tenant.PLATFORM_TENANT_ID, "default", "默认租户", "PLATFORM", 1,
                null, null, NOW, NOW);
    }

    private static Menu platformMenu(Long id, Long parentId, String name, String menuType, String permissionCode) {
        return Menu.reconstitute(id, Tenant.PLATFORM_TENANT_ID, parentId, name, "/" + id, "component/" + id,
                "icon", 0, menuType, permissionCode, 1, 1, NOW, NOW);
    }

    private static Permission permission(Long id, String permCode) {
        return Permission.reconstitute(id, Permission.ROOT_PARENT_ID, PermCode.of(permCode), permCode,
                "API", "/" + permCode, "GET", 0, Permission.STATUS_ENABLED, NOW, NOW);
    }
}

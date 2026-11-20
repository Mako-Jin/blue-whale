package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.application.model.command.CreateTenantCommand;
import com.bluewhale.auth.application.port.TenantStatusSyncPort;
import com.bluewhale.auth.application.port.PasswordHasher;
import com.bluewhale.auth.domain.aggregate.DeptAggregate;
import com.bluewhale.auth.domain.repository.DeptRepository;
import com.bluewhale.auth.domain.aggregate.MenuAggregate;
import com.bluewhale.auth.domain.repository.MenuRepository;
import com.bluewhale.auth.domain.model.vo.PermCode;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.aggregate.PermissionAggregate;
import com.bluewhale.auth.domain.repository.PermissionRepository;
import com.bluewhale.auth.domain.aggregate.RoleAggregate;
import com.bluewhale.auth.domain.aggregate.RoleConstraintAggregate;
import com.bluewhale.auth.domain.repository.RoleConstraintRepository;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.auth.domain.aggregate.TenantAggregate;
import com.bluewhale.auth.domain.repository.TenantRepository;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.auth.application.model.vo.TenantVO;
import com.bluewhale.common.core.exception.BizException;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.bluewhale.auth.application.service.TenantAppService;
import com.bluewhale.auth.application.service.TenantMembershipAppService;
import com.bluewhale.auth.application.service.TenantProvisioningAppService;
import com.bluewhale.auth.application.service.impl.TenantProvisioningAppServiceImpl;

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
    private PasswordHasher passwordHasher;

    @Mock
    private TenantStatusSyncPort tenantStatusSync;

    @Mock
    private TenantMembershipAppService tenantMembershipAppService;

    @Mock
    private RoleConstraintRepository roleConstraintRepository;

    @InjectMocks
    private TenantProvisioningAppServiceImpl service;

    @Test
    @DisplayName("开通：创建租户、部门根、菜单、默认角色与管理员，且不下发平台专属权限")
    void shouldProvisionTenantWithDefaults() {
        AtomicLong menuIdSequence = new AtomicLong(10_000L);
        when(tenantAppService.requirePlatformTenant()).thenReturn(platformTenant());
        when(tenantRepository.findByCode("acme")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(TenantAggregate.class))).thenAnswer(invocation -> {
            TenantAggregate draft = invocation.getArgument(0);
            return TenantAggregate.reconstitute(NEW_TENANT_ID, draft.getTenantCode(), draft.getTenantName(), "TENANT",
                    draft.getStatus(), draft.getExpireTime(), draft.getQuotaJson(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(deptRepository.save(any(DeptAggregate.class))).thenAnswer(invocation -> {
            DeptAggregate draft = invocation.getArgument(0);
            return DeptAggregate.reconstitute(ROOT_DEPT_ID, draft.getTenantId(), draft.getParentId(), draft.getDeptName(),
                    draft.getSort(), draft.getLeader(), draft.getCreateTime(), draft.getUpdateTime());
        });
        when(menuRepository.findAllByTenantId(TenantAggregate.PLATFORM_TENANT_ID))
                .thenReturn(List.of(platformMenu(10L, 0L, "系统管理", "DIR", null),
                        platformMenu(11L, 10L, "租户管理", "MENU", "sys:tenant:list")));
        when(menuRepository.save(any(MenuAggregate.class))).thenAnswer(invocation -> {
            MenuAggregate draft = invocation.getArgument(0);
            long id = menuIdSequence.incrementAndGet();
            return MenuAggregate.reconstitute(id, draft.getTenantId(), draft.getParentId(), draft.getName(), draft.getPath(),
                    draft.getComponent(), draft.getIcon(), draft.getSort(), draft.getMenuType(),
                    draft.getPermissionCode(), draft.getVisible(), draft.getStatus(), NOW, NOW);
        });
        when(permissionRepository.findEnabledAll()).thenReturn(List.of(
                permission(500L, "sys:tenant:list"), permission(501L, "sys:user:list")));
        when(roleRepository.save(any(RoleAggregate.class))).thenAnswer(invocation -> {
            RoleAggregate draft = invocation.getArgument(0);
            return RoleAggregate.reconstitute(ROLE_ID, draft.getTenantId(), draft.getRoleCode(), draft.getRoleName(),
                    draft.getDescription(), draft.getSortNo(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(userRepository.save(any(UserAggregate.class))).thenAnswer(invocation -> {
            UserAggregate draft = invocation.getArgument(0);
            return UserAggregate.reconstitute(ADMIN_ID, draft.getTenantId(), draft.getUsername(), draft.getPassword(),
                    draft.getNickname(), draft.getPhone(), draft.getEmail(), draft.getDeptId(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(passwordHasher.encode("initPwd")).thenReturn(CIPHER);

        TenantVO result = service.provision(new CreateTenantCommand("acme", "ACME",
                LocalDateTime.now().plusDays(30), "{\"maxUser\":10}", "admin", "initPwd", "管理员"));

        ArgumentCaptor<DeptAggregate> deptCaptor = ArgumentCaptor.forClass(DeptAggregate.class);
        verify(deptRepository, times(1)).save(deptCaptor.capture());
        assertEquals(Long.valueOf(NEW_TENANT_ID), deptCaptor.getValue().getTenantId());
        assertEquals("ACME", deptCaptor.getValue().getDeptName());

        verify(menuRepository, times(2)).save(any(MenuAggregate.class));

        ArgumentCaptor<RoleAggregate> roleCaptor = ArgumentCaptor.forClass(RoleAggregate.class);
        verify(roleRepository).save(roleCaptor.capture());
        assertEquals("tenant_admin", roleCaptor.getValue().getRoleCode().value());
        assertEquals(Long.valueOf(NEW_TENANT_ID), roleCaptor.getValue().getTenantId());
        verify(roleRepository).replacePermissions(eq(ROLE_ID), eq(List.of(501L)));

        ArgumentCaptor<UserAggregate> userCaptor = ArgumentCaptor.forClass(UserAggregate.class);
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
        when(tenantRepository.save(any(TenantAggregate.class))).thenAnswer(invocation -> {
            TenantAggregate draft = invocation.getArgument(0);
            return TenantAggregate.reconstitute(NEW_TENANT_ID, draft.getTenantCode(), draft.getTenantName(), "TENANT",
                    draft.getStatus(), draft.getExpireTime(), draft.getQuotaJson(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(deptRepository.save(any(DeptAggregate.class))).thenAnswer(invocation -> {
            DeptAggregate draft = invocation.getArgument(0);
            return DeptAggregate.reconstitute(ROOT_DEPT_ID, draft.getTenantId(), draft.getParentId(), draft.getDeptName(),
                    draft.getSort(), draft.getLeader(), draft.getCreateTime(), draft.getUpdateTime());
        });
        when(permissionRepository.findEnabledAll()).thenReturn(List.of(
                permission(140L, "sys:archive:category:list"),
                permission(44L, "sys:workflow:instance:start"),
                permission(165L, "sys:tenant:list")));
        when(roleRepository.save(any(RoleAggregate.class))).thenAnswer(invocation -> {
            RoleAggregate draft = invocation.getArgument(0);
            return RoleAggregate.reconstitute(roleIdSequence.incrementAndGet(), draft.getTenantId(), draft.getRoleCode(),
                    draft.getRoleName(), draft.getDescription(), draft.getSortNo(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        // 仅 archive_manager 模板存在，其余 16 个编码按缺失跳过
        when(roleRepository.findByTenantIdAndCode(TenantAggregate.PLATFORM_TENANT_ID, "archive_manager"))
                .thenReturn(Optional.of(RoleAggregate.reconstitute(9_000L, TenantAggregate.PLATFORM_TENANT_ID,
                        RoleCode.of("archive_manager"), "档案管理员", "档案全量", 140, RoleAggregate.STATUS_ENABLED, NOW, NOW)));
        // 模板授权含可下发 140、平台专属 165 与不存在的 999
        when(roleRepository.listPermissionIds(9_000L)).thenReturn(List.of(140L, 165L, 999L));
        when(userRepository.save(any(UserAggregate.class))).thenAnswer(invocation -> {
            UserAggregate draft = invocation.getArgument(0);
            return UserAggregate.reconstitute(ADMIN_ID, draft.getTenantId(), draft.getUsername(), draft.getPassword(),
                    draft.getNickname(), draft.getPhone(), draft.getEmail(), draft.getDeptId(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(passwordHasher.encode("initPwd")).thenReturn(CIPHER);

        service.provision(new CreateTenantCommand("acme", "ACME", null, null, "admin", "initPwd", "管理员"));

        ArgumentCaptor<RoleAggregate> roleCaptor = ArgumentCaptor.forClass(RoleAggregate.class);
        verify(roleRepository, times(2)).save(roleCaptor.capture());
        List<RoleAggregate> saved = roleCaptor.getAllValues();
        assertEquals("tenant_admin", saved.get(0).getRoleCode().value());
        RoleAggregate copied = saved.get(1);
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
    @DisplayName("业务角色模板包含 PM 角色，业务租户开通后可分配")
    void shouldIncludePmRoleTemplates() {
        assertTrue(TenantProvisioningAppService.BUSINESS_ROLE_TEMPLATE_CODES
                .containsAll(List.of("pm_manager", "pm_member")));
    }

    @Test
    @DisplayName("业务角色模板包含文档角色，业务租户开通后可分配")
    void shouldIncludeDocRoleTemplates() {
        assertTrue(TenantProvisioningAppService.BUSINESS_ROLE_TEMPLATE_CODES
                .containsAll(List.of("doc_manager", "doc_member")));
    }

    @Test
    @DisplayName("开通：按模板下发三员角色、审计码只给审计员、并播种三员互斥约束")
    void shouldProvisionThreeAdminRolesAndMutexConstraint() {
        AtomicLong roleIdSequence = new AtomicLong(300L);
        when(tenantAppService.requirePlatformTenant()).thenReturn(platformTenant());
        when(tenantRepository.findByCode("acme")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(TenantAggregate.class))).thenAnswer(invocation -> {
            TenantAggregate draft = invocation.getArgument(0);
            return TenantAggregate.reconstitute(NEW_TENANT_ID, draft.getTenantCode(), draft.getTenantName(), "TENANT",
                    draft.getStatus(), draft.getExpireTime(), draft.getQuotaJson(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(deptRepository.save(any(DeptAggregate.class))).thenAnswer(invocation -> {
            DeptAggregate draft = invocation.getArgument(0);
            return DeptAggregate.reconstitute(ROOT_DEPT_ID, draft.getTenantId(), draft.getParentId(), draft.getDeptName(),
                    draft.getSort(), draft.getLeader(), draft.getCreateTime(), draft.getUpdateTime());
        });
        // 菜单模板为空：本轮只关心角色链路
        when(menuRepository.findAllByTenantId(TenantAggregate.PLATFORM_TENANT_ID)).thenReturn(List.of());
        // 审计码（503）不在平台专属集合内，必须能下发给 audit_admin；同时不能进 tenant_admin
        when(permissionRepository.findEnabledAll()).thenReturn(List.of(
                permission(501L, "sys:user:list"), permission(503L, "sys:audit:log:list")));
        when(roleRepository.save(any(RoleAggregate.class))).thenAnswer(invocation -> {
            RoleAggregate draft = invocation.getArgument(0);
            return RoleAggregate.reconstitute(roleIdSequence.incrementAndGet(), draft.getTenantId(),
                    draft.getRoleCode(), draft.getRoleName(), draft.getDescription(), draft.getSortNo(),
                    draft.getStatus(), draft.getCreateTime(), draft.getUpdateTime());
        });
        // 三员模板齐备（其余业务角色模板缺失，按告警跳过）
        // 业务角色模板逐个查库时参数各不相同，先按「任意编码 → 空」兜底，避免严格模式下的
        // PotentialStubbingProblem；随后三条具体 stub 覆盖三员编码。
        lenient().when(roleRepository.findByTenantIdAndCode(eq(TenantAggregate.PLATFORM_TENANT_ID), anyString()))
                .thenReturn(Optional.empty());
        when(roleRepository.findByTenantIdAndCode(TenantAggregate.PLATFORM_TENANT_ID, "system_admin"))
                .thenReturn(Optional.of(threeAdminTemplate(9_001L, "system_admin", "系统管理员", 10)));
        when(roleRepository.findByTenantIdAndCode(TenantAggregate.PLATFORM_TENANT_ID, "security_admin"))
                .thenReturn(Optional.of(threeAdminTemplate(9_002L, "security_admin", "安全管理员", 11)));
        when(roleRepository.findByTenantIdAndCode(TenantAggregate.PLATFORM_TENANT_ID, "audit_admin"))
                .thenReturn(Optional.of(threeAdminTemplate(9_003L, "audit_admin", "安全审计员", 12)));
        when(roleRepository.listPermissionIds(9_001L)).thenReturn(List.of(501L));
        when(roleRepository.listPermissionIds(9_002L)).thenReturn(List.of(501L));
        when(roleRepository.listPermissionIds(9_003L)).thenReturn(List.of(503L));
        when(userRepository.save(any(UserAggregate.class))).thenAnswer(invocation -> {
            UserAggregate draft = invocation.getArgument(0);
            return UserAggregate.reconstitute(ADMIN_ID, draft.getTenantId(), draft.getUsername(), draft.getPassword(),
                    draft.getNickname(), draft.getPhone(), draft.getEmail(), draft.getDeptId(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(passwordHasher.encode("initPwd")).thenReturn(CIPHER);

        service.provision(new CreateTenantCommand("acme", "ACME", null, null, "admin", "initPwd", "管理员"));

        ArgumentCaptor<RoleAggregate> roleCaptor = ArgumentCaptor.forClass(RoleAggregate.class);
        // tenant_admin + 三员 = 4 个角色（业务角色模板全部缺失）
        verify(roleRepository, times(4)).save(roleCaptor.capture());
        List<RoleAggregate> saved = roleCaptor.getAllValues();
        assertEquals("tenant_admin", saved.get(0).getRoleCode().value());
        assertEquals(List.of("system_admin", "security_admin", "audit_admin"),
                saved.subList(1, 4).stream().map(role -> role.getRoleCode().value()).toList());
        assertEquals("SYSTEM", saved.get(1).getCategory(), "三员角色分类应随模板复制为 SYSTEM");

        // tenant_admin 被三员专属码收口：只有 sys:user:list
        verify(roleRepository).replacePermissions(301L, List.of(501L));
        verify(roleRepository).replacePermissions(302L, List.of(501L));
        verify(roleRepository).replacePermissions(303L, List.of(501L));
        // 审计员拿到审计码——这是「授权成功但看不到审计」那条静默故障的回归点
        verify(roleRepository).replacePermissions(304L, List.of(503L));

        ArgumentCaptor<RoleConstraintAggregate> constraintCaptor =
                ArgumentCaptor.forClass(RoleConstraintAggregate.class);
        verify(roleConstraintRepository).save(constraintCaptor.capture());
        RoleConstraintAggregate constraint = constraintCaptor.getValue();
        assertEquals("three_admin_mutex", constraint.getConstraintCode());
        assertEquals(List.of(302L, 303L, 304L), constraint.getRoleIds());
    }

    @Test
    @DisplayName("开通：子菜单先于父菜单返回时不报错，按轮次解析父子层级")
    void shouldCopyMenusWhenChildListedBeforeParent() {
        AtomicLong menuIdSequence = new AtomicLong(20_000L);
        when(tenantAppService.requirePlatformTenant()).thenReturn(platformTenant());
        when(tenantRepository.findByCode("acme")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(TenantAggregate.class))).thenAnswer(invocation -> {
            TenantAggregate draft = invocation.getArgument(0);
            return TenantAggregate.reconstitute(NEW_TENANT_ID, draft.getTenantCode(), draft.getTenantName(), "TENANT",
                    draft.getStatus(), draft.getExpireTime(), draft.getQuotaJson(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(deptRepository.save(any(DeptAggregate.class))).thenAnswer(invocation -> {
            DeptAggregate draft = invocation.getArgument(0);
            return DeptAggregate.reconstitute(ROOT_DEPT_ID, draft.getTenantId(), draft.getParentId(), draft.getDeptName(),
                    draft.getSort(), draft.getLeader(), draft.getCreateTime(), draft.getUpdateTime());
        });
        // 故意把子菜单排在父菜单之前，模拟无序遍历
        when(menuRepository.findAllByTenantId(TenantAggregate.PLATFORM_TENANT_ID))
                .thenReturn(List.of(platformMenu(11L, 10L, "租户管理", "MENU", null),
                        platformMenu(10L, 0L, "系统管理", "DIR", null)));
        when(menuRepository.save(any(MenuAggregate.class))).thenAnswer(invocation -> {
            MenuAggregate draft = invocation.getArgument(0);
            long id = menuIdSequence.incrementAndGet();
            return MenuAggregate.reconstitute(id, draft.getTenantId(), draft.getParentId(), draft.getName(), draft.getPath(),
                    draft.getComponent(), draft.getIcon(), draft.getSort(), draft.getMenuType(),
                    draft.getPermissionCode(), draft.getVisible(), draft.getStatus(), NOW, NOW);
        });
        when(permissionRepository.findEnabledAll()).thenReturn(List.of());
        when(roleRepository.save(any(RoleAggregate.class))).thenAnswer(invocation -> {
            RoleAggregate draft = invocation.getArgument(0);
            return RoleAggregate.reconstitute(ROLE_ID, draft.getTenantId(), draft.getRoleCode(), draft.getRoleName(),
                    draft.getDescription(), draft.getSortNo(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(userRepository.save(any(UserAggregate.class))).thenAnswer(invocation -> {
            UserAggregate draft = invocation.getArgument(0);
            return UserAggregate.reconstitute(ADMIN_ID, draft.getTenantId(), draft.getUsername(), draft.getPassword(),
                    draft.getNickname(), draft.getPhone(), draft.getEmail(), draft.getDeptId(), draft.getStatus(),
                    draft.getCreateTime(), draft.getUpdateTime());
        });
        when(passwordHasher.encode("initPwd")).thenReturn(CIPHER);

        service.provision(new CreateTenantCommand("acme", "ACME", null, null, "admin", "initPwd", "管理员"));

        ArgumentCaptor<MenuAggregate> menuCaptor = ArgumentCaptor.forClass(MenuAggregate.class);
        verify(menuRepository, times(2)).save(menuCaptor.capture());
        List<MenuAggregate> saved = menuCaptor.getAllValues();
        assertEquals("系统管理", saved.get(0).getName());
        assertEquals(Long.valueOf(MenuAggregate.ROOT_PARENT_ID), saved.get(0).getParentId());
        assertEquals("租户管理", saved.get(1).getName());
        // 子菜单在第二轮落库，父 ID 指向第一轮保存父菜单时返回的 20001
        assertEquals(Long.valueOf(20_001L), saved.get(1).getParentId());
    }

    @Test
    @DisplayName("开通：租户编码已存在时快速失败且不落库")
    void shouldRejectDuplicateTenantCode() {
        when(tenantAppService.requirePlatformTenant()).thenReturn(platformTenant());
        when(tenantRepository.findByCode("acme"))
                .thenReturn(Optional.of(TenantAggregate.reconstitute(NEW_TENANT_ID, "acme", "ACME", "TENANT", 1,
                        null, null, NOW, NOW)));

        BizException ex = assertThrows(BizException.class, () -> service.provision(
                new CreateTenantCommand("acme", "ACME", null, null, "admin", "initPwd", "管理员")));

        assertTrue(ex.getMessage().contains("租户编码已存在"));
        verify(tenantRepository, never()).save(any(TenantAggregate.class));
        verifyNoInteractions(deptRepository, menuRepository, roleRepository, userRepository,
                permissionRepository, passwordHasher);
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
                permissionRepository, passwordHasher);
    }

    private static TenantAggregate platformTenant() {
        return TenantAggregate.reconstitute(TenantAggregate.PLATFORM_TENANT_ID, "default", "默认租户", "PLATFORM", 1,
                null, null, NOW, NOW);
    }

    private static MenuAggregate platformMenu(Long id, Long parentId, String name, String menuType, String permissionCode) {
        return MenuAggregate.reconstitute(id, TenantAggregate.PLATFORM_TENANT_ID, parentId, name, "/" + id, "component/" + id,
                "icon", 0, menuType, permissionCode, 1, 1, NOW, NOW);
    }

    private static PermissionAggregate permission(Long id, String permCode) {
        return PermissionAggregate.reconstitute(id, PermissionAggregate.ROOT_PARENT_ID, PermCode.of(permCode), permCode,
                "API", "/" + permCode, "GET", 0, PermissionAggregate.STATUS_ENABLED, NOW, NOW);
    }

    /**
     * 构造平台租户的三员角色模板（category = SYSTEM，与 V36 迁移一致）
     *
     * @param id 模板角色 ID
     * @param roleCode 角色编码
     * @param roleName 角色名称
     * @param sortNo 排序号
     * @return 三员角色模板聚合
     */
    private static RoleAggregate threeAdminTemplate(Long id, String roleCode, String roleName, int sortNo) {
        return RoleAggregate.reconstituteWithHierarchy(id, TenantAggregate.PLATFORM_TENANT_ID,
                RoleCode.of(roleCode), roleName, "三员模板", sortNo, RoleAggregate.STATUS_ENABLED,
                0L, 1, "NONE", "SYSTEM", NOW, NOW);
    }
}

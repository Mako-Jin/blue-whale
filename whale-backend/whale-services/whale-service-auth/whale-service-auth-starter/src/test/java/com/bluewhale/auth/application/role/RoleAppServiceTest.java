package com.bluewhale.auth.application.role;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.AssignRolePermissionsCommand;
import com.bluewhale.auth.application.model.command.CreateRoleCommand;
import com.bluewhale.auth.application.model.command.UpdateRoleCommand;
import com.bluewhale.auth.application.model.query.RolePageQuery;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.RoleDomainEvent;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.repository.PermissionRepository;
import com.bluewhale.auth.domain.aggregate.RoleAggregate;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.auth.application.model.vo.RoleVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bluewhale.auth.application.service.RoleAppService;
import com.bluewhale.auth.application.service.impl.RoleAppServiceImpl;

/**
 * {@link RoleAppService} 单元测试：角色分页/详情/增删改、权限分配与缓存失效（AUTH-17 / T-DDD-9）。
 *
 * <p>纯 Mockito 单测，不启动 Spring 上下文、不连库；校验文案与旧 {@code SysRoleServiceImpl} 一致。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class RoleAppServiceTest {

    private static final long ROLE_ID = 7L;
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 9, 0, 0);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 1, 2, 9, 0, 0);

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private RoleAppServiceImpl roleAppService;

    @BeforeEach
    void bindTenant() {
        TenantContext.set(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static RoleAggregate role(String name) {
        return RoleAggregate.reconstitute(ROLE_ID, 1L, RoleCode.of("test_role"), name, "描述", 1,
                RoleAggregate.STATUS_ENABLED, CREATED, UPDATED);
    }

    @Test
    @DisplayName("page：透传分页条件并逐条映射为 RoleVO")
    void shouldPageRoles() {
        when(roleRepository.page(1L, 10L, "test"))
                .thenReturn(new RoleRepository.RolePage(List.of(role("测试角色")), 1L));

        PageResult<RoleVO> result = roleAppService.page(new RolePageQuery(1L, 10L, "test"));

        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
        assertEquals("test_role", result.getRecords().get(0).getRoleCode());
    }

    @Test
    @DisplayName("getDetail：命中时返回角色视图对象")
    void shouldGetDetailWhenExists() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role("测试角色")));

        RoleVO vo = roleAppService.getDetail(ROLE_ID);

        assertEquals(ROLE_ID, vo.getId());
        assertEquals("测试角色", vo.getRoleName());
    }

    @Test
    @DisplayName("getDetail：不存在时抛 BizException(NOT_FOUND)")
    void shouldRejectGetDetailWhenMissing() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> roleAppService.getDetail(ROLE_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals(ErrorCode.NOT_FOUND.getMessage(), ex.getMessage());
    }

    @Test
    @DisplayName("create：角色编码已存在时抛 BizException 且不落库")
    void shouldRejectCreateWhenCodeExists() {
        when(roleRepository.existsByCode("test_role")).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> roleAppService.create(
                new CreateRoleCommand("test_role", "测试角色", null, null, null)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("角色编码已存在", ex.getMessage());
        verify(roleRepository, never()).save(any(RoleAggregate.class));
    }

    @Test
    @DisplayName("create：编码可用时落库并取默认排序/状态，发布 CREATED 事件")
    void shouldCreateRoleWhenCodeAvailable() {
        when(roleRepository.existsByCode("test_role")).thenReturn(false);
        when(roleRepository.save(any(RoleAggregate.class))).thenReturn(RoleAggregate.reconstitute(
                ROLE_ID, 1L, RoleCode.of("test_role"), "测试角色", null, 0, RoleAggregate.STATUS_ENABLED, CREATED, UPDATED));

        RoleVO vo = roleAppService.create(new CreateRoleCommand("test_role", "测试角色", null, null, null));

        assertEquals("test_role", vo.getRoleCode());
        assertEquals("测试角色", vo.getRoleName());
        assertEquals(0, vo.getSortNo());
        assertEquals(RoleAggregate.STATUS_ENABLED, vo.getStatus());
        verify(roleRepository).save(any(RoleAggregate.class));
        verify(eventPublisher).publish(new RoleDomainEvent(ROLE_ID, ChangeType.CREATED));
    }

    @Test
    @DisplayName("update：名称/描述全量覆盖，排序与状态非空时变更")
    void shouldUpdateRoleFields() {
        RoleAggregate stored = role("旧名");
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(stored));

        RoleVO vo = roleAppService.update(ROLE_ID,
                new UpdateRoleCommand("新名", "新描述", 9, RoleAggregate.STATUS_DISABLED));

        assertEquals("新名", vo.getRoleName());
        assertEquals("新描述", vo.getDescription());
        assertEquals(9, vo.getSortNo());
        assertEquals(RoleAggregate.STATUS_DISABLED, vo.getStatus());
        verify(roleRepository).save(stored);
        verify(eventPublisher).publish(new RoleDomainEvent(ROLE_ID, ChangeType.STATUS_CHANGED));
    }

    @Test
    @DisplayName("update：状态未变化时发布 UPDATED 事件，不触发角色级缓存失效")
    void shouldPublishUpdatedWhenStatusUnchanged() {
        RoleAggregate stored = role("旧名");
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(stored));

        roleAppService.update(ROLE_ID, new UpdateRoleCommand("新名", "新描述", 9, RoleAggregate.STATUS_ENABLED));

        assertEquals("新名", stored.getRoleName());
        verify(eventPublisher).publish(new RoleDomainEvent(ROLE_ID, ChangeType.UPDATED));
    }

    @Test
    @DisplayName("delete：删除角色并发布 DELETED 事件")
    void shouldDeleteRoleAndPublishEvent() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role("测试角色")));

        roleAppService.delete(ROLE_ID);

        verify(roleRepository).deleteById(ROLE_ID);
        verify(eventPublisher).publish(new RoleDomainEvent(ROLE_ID, ChangeType.DELETED));
    }

    @Test
    @DisplayName("assignPermissions：存在无效权限 ID 时抛 BizException 且不写关联、不发布事件")
    void shouldRejectAssignPermissionsWhenPermissionInvalid() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role("测试角色")));
        List<Long> permissionIds = List.of(10L, 11L);
        when(permissionRepository.countByIds(anyList())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> roleAppService.assignPermissions(ROLE_ID, new AssignRolePermissionsCommand(permissionIds)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("存在无效的权限ID", ex.getMessage());
        verify(roleRepository, never()).replacePermissions(any(), anyList());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("assignPermissions：权限全部有效时重建关联并发布 PERMISSIONS_ASSIGNED 事件")
    void shouldAssignPermissionsAndPublishEvent() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role("测试角色")));
        List<Long> permissionIds = List.of(10L, 11L);
        when(permissionRepository.countByIds(anyList())).thenReturn(2L);

        roleAppService.assignPermissions(ROLE_ID, new AssignRolePermissionsCommand(permissionIds));

        verify(roleRepository).replacePermissions(ROLE_ID, permissionIds);
        verify(eventPublisher).publish(new RoleDomainEvent(ROLE_ID, ChangeType.PERMISSIONS_ASSIGNED));
    }

    @Test
    @DisplayName("listPermissionIds：委托仓储返回权限 ID 列表")
    void shouldListPermissionIds() {
        when(roleRepository.listPermissionIds(ROLE_ID)).thenReturn(List.of(10L));

        assertEquals(List.of(10L), roleAppService.listPermissionIds(ROLE_ID));
    }

    @Test
    @DisplayName("listRoleCodesByUserId：委托仓储返回启用角色编码")
    void shouldDelegateListRoleCodesByUserId() {
        when(roleRepository.listRoleCodesByUserId(ROLE_ID)).thenReturn(List.of("test_role", "ops"));

        assertEquals(List.of("test_role", "ops"), roleAppService.listRoleCodesByUserId(ROLE_ID));
        verify(roleRepository).listRoleCodesByUserId(ROLE_ID);
    }

    @Test
    @DisplayName("listPermissionCodesByUserId：委托仓储返回启用权限码")
    void shouldDelegateListPermissionCodesByUserId() {
        when(roleRepository.listPermissionCodesByUserId(ROLE_ID))
                .thenReturn(List.of("sys:user:list", "sys:role:list"));

        assertEquals(List.of("sys:user:list", "sys:role:list"),
                roleAppService.listPermissionCodesByUserId(ROLE_ID));
        verify(roleRepository).listPermissionCodesByUserId(ROLE_ID);
    }

    // ------------------------------------------------------------------
    // 三员分立（等保 2.0）：内置角色保护与视图暴露
    // ------------------------------------------------------------------

    /**
     * 构造指定角色编码的角色聚合（用于三员与内置角色用例）
     *
     * @param roleCode 角色编码（system_admin / security_admin / audit_admin / tenant_admin …）
     * @return 角色聚合
     */
    private static RoleAggregate roleOf(String roleCode) {
        return RoleAggregate.reconstitute(ROLE_ID, 1L, RoleCode.of(roleCode), roleCode, "描述", 10,
                RoleAggregate.STATUS_ENABLED, CREATED, UPDATED);
    }

    @Test
    @DisplayName("delete：内置角色（三员 / 租户管理员）拒绝删除且不落库、不发事件")
    void shouldRejectDeleteBuiltInRole() {
        for (String code : List.of("system_admin", "security_admin", "audit_admin", "tenant_admin")) {
            when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(roleOf(code)));

            BizException ex = assertThrows(BizException.class, () -> roleAppService.delete(ROLE_ID));

            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
            assertTrue(ex.getMessage().contains("内置角色不允许删除"), code + " 应被拒绝删除");
        }
        verify(roleRepository, never()).deleteById(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("assignPermissions：三员角色拒绝手工调整权限集（防止职责分离被绕过）")
    void shouldRejectAssignPermissionsForThreeAdmin() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(roleOf("audit_admin")));

        BizException ex = assertThrows(BizException.class, () -> roleAppService.assignPermissions(
                ROLE_ID, new AssignRolePermissionsCommand(List.of(10L))));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("三员角色权限集由内置职责矩阵决定"));
        verify(roleRepository, never()).replacePermissions(any(), anyList());
    }

    @Test
    @DisplayName("update：内置角色改名被拒绝，同值提交（只改描述）放行")
    void shouldRejectRenameBuiltInRoleButAllowDescriptionChange() {
        RoleAggregate stored = roleOf("system_admin");
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(stored));

        // 真改名：拒绝
        assertThrows(BizException.class, () -> roleAppService.update(ROLE_ID,
                new UpdateRoleCommand("运维管理员", "新描述", 10, RoleAggregate.STATUS_ENABLED)));
        assertEquals("system_admin", stored.getRoleName());

        // 同值提交：只改描述，放行
        RoleVO vo = roleAppService.update(ROLE_ID,
                new UpdateRoleCommand("system_admin", "职责说明已更新", 10, RoleAggregate.STATUS_ENABLED));
        assertEquals("职责说明已更新", vo.getDescription());
    }

    @Test
    @DisplayName("update：三员角色不允许停用（停用等于让职责分离岗位缺岗）")
    void shouldRejectDisableThreeAdminRole() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(roleOf("audit_admin")));

        assertThrows(BizException.class, () -> roleAppService.update(ROLE_ID,
                new UpdateRoleCommand("audit_admin", "描述", 12, RoleAggregate.STATUS_DISABLED)));
        verify(roleRepository, never()).save(any(RoleAggregate.class));
    }

    @Test
    @DisplayName("RoleVO：暴露 builtIn / threeAdmin 供前端置灰与标签渲染")
    void shouldExposeBuiltInFlagsInVo() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(roleOf("security_admin")));

        RoleVO vo = roleAppService.getDetail(ROLE_ID);

        assertTrue(vo.getBuiltIn());
        assertTrue(vo.getThreeAdmin());

        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(roleOf("test_role")));
        RoleVO normal = roleAppService.getDetail(ROLE_ID);
        assertFalse(normal.getBuiltIn());
        assertFalse(normal.getThreeAdmin());
    }
}

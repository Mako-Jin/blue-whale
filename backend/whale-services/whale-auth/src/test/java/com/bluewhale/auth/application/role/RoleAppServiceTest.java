package com.bluewhale.auth.application.role;

import com.bluewhale.auth.application.role.command.AssignRolePermissionsCommand;
import com.bluewhale.auth.application.role.command.CreateRoleCommand;
import com.bluewhale.auth.application.role.command.UpdateRoleCommand;
import com.bluewhale.auth.application.role.query.RolePageQuery;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.permission.repository.PermissionRepository;
import com.bluewhale.auth.domain.role.aggregate.Role;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.auth.security.PermissionCache;
import com.bluewhale.auth.vo.RoleVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private PermissionCache permissionCache;

    @InjectMocks
    private RoleAppService roleAppService;

    private static Role role(String name) {
        return Role.reconstitute(ROLE_ID, RoleCode.of("test_role"), name, "描述", 1,
                Role.STATUS_ENABLED, CREATED, UPDATED);
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
        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    @DisplayName("create：编码可用时落库并取默认排序/状态")
    void shouldCreateRoleWhenCodeAvailable() {
        when(roleRepository.existsByCode("test_role")).thenReturn(false);

        RoleVO vo = roleAppService.create(new CreateRoleCommand("test_role", "测试角色", null, null, null));

        assertEquals("test_role", vo.getRoleCode());
        assertEquals("测试角色", vo.getRoleName());
        assertEquals(0, vo.getSortNo());
        assertEquals(Role.STATUS_ENABLED, vo.getStatus());
        verify(roleRepository).save(any(Role.class));
    }

    @Test
    @DisplayName("update：名称/描述全量覆盖，排序与状态非空时变更")
    void shouldUpdateRoleFields() {
        Role stored = role("旧名");
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(stored));

        RoleVO vo = roleAppService.update(ROLE_ID,
                new UpdateRoleCommand("新名", "新描述", 9, Role.STATUS_DISABLED));

        assertEquals("新名", vo.getRoleName());
        assertEquals("新描述", vo.getDescription());
        assertEquals(9, vo.getSortNo());
        assertEquals(Role.STATUS_DISABLED, vo.getStatus());
        verify(roleRepository).save(stored);
    }

    @Test
    @DisplayName("delete：删除角色并失效该角色下用户的权限缓存")
    void shouldDeleteRoleAndEvictByRole() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role("测试角色")));

        roleAppService.delete(ROLE_ID);

        verify(roleRepository).deleteById(ROLE_ID);
        verify(permissionCache).evictByRole(ROLE_ID);
    }

    @Test
    @DisplayName("assignPermissions：存在无效权限 ID 时抛 BizException 且不写关联、不失效缓存")
    void shouldRejectAssignPermissionsWhenPermissionInvalid() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role("测试角色")));
        List<Long> permissionIds = List.of(10L, 11L);
        when(permissionRepository.countByIds(anyList())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> roleAppService.assignPermissions(ROLE_ID, new AssignRolePermissionsCommand(permissionIds)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("存在无效的权限ID", ex.getMessage());
        verify(roleRepository, never()).replacePermissions(any(), anyList());
        verify(permissionCache, never()).evictByRole(any());
    }

    @Test
    @DisplayName("assignPermissions：权限全部有效时重建关联并失效角色缓存")
    void shouldAssignPermissionsAndEvictByRole() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role("测试角色")));
        List<Long> permissionIds = List.of(10L, 11L);
        when(permissionRepository.countByIds(anyList())).thenReturn(2L);

        roleAppService.assignPermissions(ROLE_ID, new AssignRolePermissionsCommand(permissionIds));

        verify(roleRepository).replacePermissions(ROLE_ID, permissionIds);
        verify(permissionCache).evictByRole(ROLE_ID);
    }

    @Test
    @DisplayName("listPermissionIds：委托仓储返回权限 ID 列表")
    void shouldListPermissionIds() {
        when(roleRepository.listPermissionIds(ROLE_ID)).thenReturn(List.of(10L));

        assertEquals(List.of(10L), roleAppService.listPermissionIds(ROLE_ID));
    }
}

package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.aggregate.Role;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.auth.domain.aggregate.Tenant;
import com.bluewhale.auth.domain.repository.TenantRepository;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.auth.domain.repository.UserTenantRepository;
import com.bluewhale.auth.application.model.result.TenantResult;
import com.bluewhale.auth.application.model.vo.TenantMemberVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.bluewhale.auth.application.service.TenantAppService;
import com.bluewhale.auth.application.service.TenantMembershipAppService;
import com.bluewhale.auth.application.service.impl.TenantMembershipAppServiceImpl;

/**
 * {@link TenantMembershipAppService} 单元测试（Wave 5.54 Phase 5.1：一人多租户）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TenantMembershipAppServiceTest {

    private static final Long USER_ID = 1251323419503984640L;

    @Mock
    private UserTenantRepository userTenantRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private TenantAppService tenantAppService;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @InjectMocks
    private TenantMembershipAppServiceImpl service;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("requireSwitchable：非成员切换返回 403，且不读取租户")
    void requireSwitchableShouldRejectNonMember() {
        when(userTenantRepository.existsEnabled(USER_ID, 2L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.requireSwitchable(USER_ID, 2L));

        assertEquals(403, ex.getCode());
        verifyNoInteractions(tenantRepository);
    }

    @Test
    @DisplayName("requireSwitchable：目标租户停用或过期返回 403")
    void requireSwitchableShouldRejectUnavailableTenant() {
        Tenant tenant = tenant(2L, "t2", "租户2", false);
        when(userTenantRepository.existsEnabled(USER_ID, 2L)).thenReturn(true);
        when(tenantRepository.findById(2L)).thenReturn(Optional.of(tenant));

        BizException ex = assertThrows(BizException.class, () -> service.requireSwitchable(USER_ID, 2L));

        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("requireEnabledMembership：关系被撤销时 refresh 返回 401")
    void requireEnabledMembershipShouldRejectRevokedRelation() {
        when(userTenantRepository.existsEnabled(USER_ID, 1L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.requireEnabledMembership(USER_ID, 1L));

        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("listMyTenants：过滤不可用租户并标记当前租户")
    void listMyTenantsShouldFilterUnavailableAndMarkCurrent() {
        Tenant available = tenant(1L, "t1", "租户1", true);
        Tenant expired = tenant(2L, "t2", "租户2", false);
        when(userTenantRepository.listEnabledTenantIds(USER_ID)).thenReturn(List.of(1L, 2L));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(available));
        when(tenantRepository.findById(2L)).thenReturn(Optional.of(expired));

        List<TenantResult> tenants = service.listMyTenants(USER_ID, 1L);

        assertEquals(1, tenants.size());
        assertEquals(1L, tenants.get(0).tenantId());
        assertTrue(tenants.get(0).current());
    }

    @Test
    @DisplayName("listMembers：只返回本租户启用关系，角色按当前租户过滤（3 次批量查询组装）")
    void listMembersShouldReturnTenantMembersOnly() {
        TenantContext.set(5L);
        Long userId2 = 1251323419503984641L;
        when(userTenantRepository.listEnabledUserIds(5L)).thenReturn(List.of(USER_ID, userId2));
        UserAggregate user1 = user(USER_ID, "alice");
        when(userRepository.findByIdsAcrossTenants(List.of(USER_ID, userId2)))
                .thenReturn(List.of(user1));
        when(userRepository.listRoleIdsByUserIds(List.of(USER_ID, userId2)))
                .thenReturn(Map.of(USER_ID, List.of(10L, 20L)));
        Role tenantRole = role(10L, 5L, "tenant_admin");
        Role platformRole = role(20L, 1L, "platform_admin");
        when(roleRepository.listEnabledByIds(List.of(10L, 20L)))
                .thenReturn(List.of(tenantRole, platformRole));

        List<TenantMemberVO> members = service.listMembers();

        assertEquals(1, members.size());
        assertEquals(USER_ID, members.get(0).getUserId());
        assertEquals(1, members.get(0).getRoles().size());
        assertEquals("tenant_admin", members.get(0).getRoles().get(0).getRoleCode());
    }

    @Test
    @DisplayName("assignMemberRoles：覆盖角色并发布目标租户缓存失效事件")
    void assignMemberRolesShouldPublishTenantEvent() {
        TenantContext.set(5L);
        UserAggregate user = user(USER_ID, "alice");
        when(userTenantRepository.existsEnabled(USER_ID, 5L)).thenReturn(true);
        when(userRepository.findByIdAcrossTenants(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.countByIds(List.of(10L))).thenReturn(1L);

        service.assignMemberRoles(USER_ID, List.of(10L));

        verify(userRepository).replaceRoles(USER_ID, List.of(10L));
        verify(domainEventPublisher).publish(new UserDomainEvent(USER_ID,
                ChangeType.ROLES_ASSIGNED, 5L));
    }

    @Test
    @DisplayName("assignMemberRoles：非本租户成员返回 404，不写角色关联")
    void assignMemberRolesShouldRejectNonMember() {
        TenantContext.set(5L);
        when(userTenantRepository.existsEnabled(USER_ID, 5L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> service.assignMemberRoles(USER_ID, List.of(10L)));

        assertEquals(404, ex.getCode());
        verify(userRepository, never()).replaceRoles(any(), any());
    }

    @Test
    @DisplayName("assignMemberRoles：角色不属于当前租户返回 400")
    void assignMemberRolesShouldRejectForeignRole() {
        TenantContext.set(5L);
        UserAggregate user = user(USER_ID, "alice");
        when(userTenantRepository.existsEnabled(USER_ID, 5L)).thenReturn(true);
        when(userRepository.findByIdAcrossTenants(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.countByIds(List.of(20L))).thenReturn(0L);

        BizException ex = assertThrows(BizException.class,
                () -> service.assignMemberRoles(USER_ID, List.of(20L)));

        assertEquals(400, ex.getCode());
        verify(userRepository, never()).replaceRoles(any(), any());
    }

    @Test
    @DisplayName("bindTenant：目标租户停用返回 400，不写关系")
    void bindTenantShouldRejectUnavailableTenant() {
        UserAggregate user = user(USER_ID, "alice");
        Tenant tenant = tenant(2L, "t2", "租户2", false);
        when(userRepository.findByIdAcrossTenants(USER_ID)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(2L)).thenReturn(Optional.of(tenant));

        BizException ex = assertThrows(BizException.class, () -> service.bindTenant(USER_ID, 2L));

        assertEquals(400, ex.getCode());
        verify(userTenantRepository, never()).ensureEnabled(any(), any());
    }

    @Test
    @DisplayName("bindTenant：写入关系并发布权限缓存失效事件")
    void bindTenantShouldPersistRelation() {
        UserAggregate user = user(USER_ID, "alice");
        Tenant tenant = tenant(2L, "t2", "租户2", true);
        when(userRepository.findByIdAcrossTenants(USER_ID)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(2L)).thenReturn(Optional.of(tenant));

        service.bindTenant(USER_ID, 2L);

        verify(userTenantRepository).ensureEnabled(USER_ID, 2L);
        verify(domainEventPublisher).publish(new UserDomainEvent(USER_ID,
                ChangeType.ROLES_ASSIGNED, 2L));
    }

    @Test
    @DisplayName("unbindTenant：软停用关系、清理本租户角色并发布解绑事件")
    void unbindTenantShouldPublishTenantEvent() {
        UserAggregate user = user(USER_ID, "alice");
        when(userRepository.findByIdAcrossTenants(USER_ID)).thenReturn(Optional.of(user));
        when(userTenantRepository.exists(USER_ID, 2L)).thenReturn(true);

        service.unbindTenant(USER_ID, 2L);

        verify(userTenantRepository).disable(USER_ID, 2L);
        verify(userRepository).removeRolesInTenant(USER_ID, 2L);
        verify(domainEventPublisher).publish(new UserDomainEvent(USER_ID,
                ChangeType.TENANT_UNBOUND, 2L));
    }

    @Test
    @DisplayName("unbindTenant：首页租户关系禁止删除")
    void unbindTenantShouldRejectHomeRelation() {
        UserAggregate user = user(USER_ID, "alice");
        when(userRepository.findByIdAcrossTenants(USER_ID)).thenReturn(Optional.of(user));

        BizException ex = assertThrows(BizException.class, () -> service.unbindTenant(USER_ID, 1L));

        assertEquals(400, ex.getCode());
        verify(userTenantRepository, never()).disable(any(), any());
        verify(userRepository, never()).removeRolesInTenant(any(), any());
    }

    @Test
    @DisplayName("unbindTenant：关系不存在返回 404")
    void unbindTenantShouldRejectMissingRelation() {
        UserAggregate user = user(USER_ID, "alice");
        when(userRepository.findByIdAcrossTenants(USER_ID)).thenReturn(Optional.of(user));
        when(userTenantRepository.exists(USER_ID, 2L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.unbindTenant(USER_ID, 2L));

        assertEquals(404, ex.getCode());
        verify(userTenantRepository, never()).disable(any(), any());
    }

    private static UserAggregate user(Long id, String username) {
        UserAggregate user = mock(UserAggregate.class);
        org.mockito.Mockito.lenient().when(user.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(user.getUsername()).thenReturn(Username.of(username));
        org.mockito.Mockito.lenient().when(user.getNickname()).thenReturn(username);
        org.mockito.Mockito.lenient().when(user.getPhone()).thenReturn("13800000000");
        org.mockito.Mockito.lenient().when(user.getStatus()).thenReturn(1);
        org.mockito.Mockito.lenient().when(user.getTenantId()).thenReturn(1L);
        // 应用层会先经 User#assignRoles 归一化（去重去空）再落库，mock 按同等语义透传
        org.mockito.Mockito.lenient().when(user.assignRoles(org.mockito.ArgumentMatchers.anyCollection()))
                .thenAnswer(inv -> ((java.util.Collection<Long>) inv.getArgument(0)).stream()
                        .filter(java.util.Objects::nonNull).distinct().toList());
        return user;
    }

    private static Role role(Long id, Long tenantId, String roleCode) {
        Role role = mock(Role.class);
        org.mockito.Mockito.lenient().when(role.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(role.getTenantId()).thenReturn(tenantId);
        org.mockito.Mockito.lenient().when(role.getRoleCode()).thenReturn(RoleCode.of(roleCode));
        org.mockito.Mockito.lenient().when(role.getRoleName()).thenReturn(roleCode);
        return role;
    }

    private static Tenant tenant(Long id, String code, String name, boolean available) {
        Tenant tenant = org.mockito.Mockito.mock(Tenant.class);
        org.mockito.Mockito.lenient().when(tenant.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(tenant.getTenantCode()).thenReturn(code);
        org.mockito.Mockito.lenient().when(tenant.getTenantName()).thenReturn(name);
        org.mockito.Mockito.lenient().when(tenant.isAvailableAt(any(LocalDateTime.class))).thenReturn(available);
        return tenant;
    }
}

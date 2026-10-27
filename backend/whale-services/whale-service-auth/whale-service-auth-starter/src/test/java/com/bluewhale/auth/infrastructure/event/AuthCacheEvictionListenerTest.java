package com.bluewhale.auth.infrastructure.event;

import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.PermissionDomainEvent;
import com.bluewhale.auth.domain.event.RoleDomainEvent;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.security.PermissionCache;
import com.bluewhale.auth.security.TenantMembershipRevocationStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link AuthCacheEvictionListener} 单元测试：事件类型与变更类型的缓存失效映射（AUTH-17 / T-DDD-11）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuthCacheEvictionListenerTest {

    private static final long AGGREGATE_ID = 5L;

    @Mock
    private PermissionCache permissionCache;

    @Mock
    private TenantMembershipRevocationStore revocationStore;

    @InjectMocks
    private AuthCacheEvictionListener listener;

    @Test
    @DisplayName("用户 STATUS_CHANGED：失效该用户缓存")
    void shouldEvictUserOnStatusChanged() {
        listener.onUserEvent(new UserDomainEvent(AGGREGATE_ID, ChangeType.STATUS_CHANGED));

        verify(permissionCache).evictUser(AGGREGATE_ID);
    }

    @Test
    @DisplayName("用户 DELETED：失效该用户缓存")
    void shouldEvictUserOnDeleted() {
        listener.onUserEvent(new UserDomainEvent(AGGREGATE_ID, ChangeType.DELETED));

        verify(permissionCache).evictUser(AGGREGATE_ID);
    }

    @Test
    @DisplayName("用户 ROLES_ASSIGNED：失效该用户缓存")
    void shouldEvictUserOnRolesAssigned() {
        listener.onUserEvent(new UserDomainEvent(AGGREGATE_ID, ChangeType.ROLES_ASSIGNED));

        verify(permissionCache).evictUser(AGGREGATE_ID);
    }

    @Test
    @DisplayName("用户事件携带租户：按显式租户失效缓存")
    void shouldEvictUserInTenantWhenTenantSpecified() {
        listener.onUserEvent(new UserDomainEvent(AGGREGATE_ID, ChangeType.ROLES_ASSIGNED, 9L));

        verify(permissionCache).evictUserInTenant(9L, AGGREGATE_ID);
        verify(permissionCache, never()).evictUser(anyLong());
    }

    @Test
    @DisplayName("成员解绑：失效目标租户缓存并写入存量令牌撤销时间")
    void shouldEvictAndRevokeWhenTenantUnbound() {
        listener.onUserEvent(new UserDomainEvent(AGGREGATE_ID, ChangeType.TENANT_UNBOUND, 9L));

        verify(permissionCache).evictUserInTenant(9L, AGGREGATE_ID);
        verify(revocationStore).revoke(9L, AGGREGATE_ID);
    }

    @Test
    @DisplayName("成员解绑缺少租户：仅按当前上下文失效缓存，不写撤销时间")
    void shouldSkipRevocationWhenTenantUnboundWithoutTenant() {
        listener.onUserEvent(new UserDomainEvent(AGGREGATE_ID, ChangeType.TENANT_UNBOUND));

        verify(permissionCache).evictUser(AGGREGATE_ID);
        verify(revocationStore, never()).revoke(anyLong(), anyLong());
    }

    @Test
    @DisplayName("角色分配：只失效缓存，不写成员撤销时间")
    void shouldNotRevokeOnRolesAssigned() {
        listener.onUserEvent(new UserDomainEvent(AGGREGATE_ID, ChangeType.ROLES_ASSIGNED, 9L));

        verify(revocationStore, never()).revoke(anyLong(), anyLong());
    }

    @Test
    @DisplayName("用户 UPDATED：不触发缓存失效")
    void shouldNotEvictUserOnUpdated() {
        listener.onUserEvent(new UserDomainEvent(AGGREGATE_ID, ChangeType.UPDATED));

        verify(permissionCache, never()).evictUser(anyLong());
    }

    @Test
    @DisplayName("角色 DELETED：失效该角色下用户缓存")
    void shouldEvictByRoleOnDeleted() {
        listener.onRoleEvent(new RoleDomainEvent(AGGREGATE_ID, ChangeType.DELETED));

        verify(permissionCache).evictByRole(AGGREGATE_ID);
    }

    @Test
    @DisplayName("角色 PERMISSIONS_ASSIGNED：失效该角色下用户缓存")
    void shouldEvictByRoleOnPermissionsAssigned() {
        listener.onRoleEvent(new RoleDomainEvent(AGGREGATE_ID, ChangeType.PERMISSIONS_ASSIGNED));

        verify(permissionCache).evictByRole(AGGREGATE_ID);
    }

    @Test
    @DisplayName("角色 STATUS_CHANGED：停用/启用后失效该角色下用户缓存")
    void shouldEvictByRoleOnStatusChanged() {
        listener.onRoleEvent(new RoleDomainEvent(AGGREGATE_ID, ChangeType.STATUS_CHANGED));

        verify(permissionCache).evictByRole(AGGREGATE_ID);
    }

    @Test
    @DisplayName("角色 UPDATED：不触发缓存失效")
    void shouldNotEvictByRoleOnUpdated() {
        listener.onRoleEvent(new RoleDomainEvent(AGGREGATE_ID, ChangeType.UPDATED));

        verify(permissionCache, never()).evictByRole(anyLong());
    }

    @Test
    @DisplayName("权限点 UPDATED：失效持有该点位的用户缓存")
    void shouldEvictByPermissionOnUpdated() {
        listener.onPermissionEvent(new PermissionDomainEvent(AGGREGATE_ID, ChangeType.UPDATED));

        verify(permissionCache).evictByPermission(AGGREGATE_ID);
    }

    @Test
    @DisplayName("权限点 DELETED：失效持有该点位的用户缓存")
    void shouldEvictByPermissionOnDeleted() {
        listener.onPermissionEvent(new PermissionDomainEvent(AGGREGATE_ID, ChangeType.DELETED));

        verify(permissionCache).evictByPermission(AGGREGATE_ID);
    }

    @Test
    @DisplayName("权限点 CREATED：不触发缓存失效")
    void shouldNotEvictByPermissionOnCreated() {
        listener.onPermissionEvent(new PermissionDomainEvent(AGGREGATE_ID, ChangeType.CREATED));

        verify(permissionCache, never()).evictByPermission(anyLong());
    }
}

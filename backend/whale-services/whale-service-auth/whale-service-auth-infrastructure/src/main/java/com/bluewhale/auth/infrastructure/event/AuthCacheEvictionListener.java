package com.bluewhale.auth.infrastructure.event;

import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.PermissionDomainEvent;
import com.bluewhale.auth.domain.event.RoleDomainEvent;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.security.PermissionCache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 权限缓存失效监听器（AUTH-17 / T-DDD-11）。
 *
 * <p>接管原应用服务中直接调用 {@link PermissionCache} 的失效逻辑：仅在事务提交后执行，
 * 无事务时回退为立即执行（{@code fallbackExecution = true}），避免「事务未提交即删缓存」。
 * 菜单、部门与 ABAC 属性事件不触发缓存失效。</p>
 *
 * @author BlueWhale
 */
@Component
public class AuthCacheEvictionListener {

    /** 权限判定缓存 */
    private final PermissionCache permissionCache;

    /**
     * 构造器注入
     *
     * @param permissionCache 权限判定缓存
     */
    public AuthCacheEvictionListener(PermissionCache permissionCache) {
        this.permissionCache = permissionCache;
    }

    /**
     * 处理用户事件：启停、删除与角色变更时失效该用户缓存。
     *
     * <p>事件携带租户 ID 时（成员绑定/解绑等跨租户操作），按显式租户构造 Key；
     * 否则沿用当前租户上下文。监听器本身已在事务提交后执行，失效动作不再二次延迟。</p>
     *
     * @param event 用户领域事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserEvent(UserDomainEvent event) {
        ChangeType changeType = event.changeType();
        if (changeType == ChangeType.STATUS_CHANGED
                || changeType == ChangeType.DELETED
                || changeType == ChangeType.ROLES_ASSIGNED) {
            if (event.tenantId() != null) {
                permissionCache.evictUserInTenant(event.tenantId(), event.aggregateId());
            } else {
                permissionCache.evictUser(event.aggregateId());
            }
        }
    }

    /**
     * 处理角色事件：删除、权限变更与启停时失效该角色下用户的缓存
     *
     * <p>角色停用后其权限码不再生效，必须立即失效对应用户缓存，不能等待 TTL 过期。</p>
     *
     * @param event 角色领域事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoleEvent(RoleDomainEvent event) {
        ChangeType changeType = event.changeType();
        if (changeType == ChangeType.DELETED
                || changeType == ChangeType.PERMISSIONS_ASSIGNED
                || changeType == ChangeType.STATUS_CHANGED) {
            permissionCache.evictByRole(event.aggregateId());
        }
    }

    /**
     * 处理权限点事件：更新与删除时失效持有该权限点的用户缓存
     *
     * @param event 权限点领域事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPermissionEvent(PermissionDomainEvent event) {
        ChangeType changeType = event.changeType();
        if (changeType == ChangeType.UPDATED || changeType == ChangeType.DELETED) {
            permissionCache.evictByPermission(event.aggregateId());
        }
    }
}

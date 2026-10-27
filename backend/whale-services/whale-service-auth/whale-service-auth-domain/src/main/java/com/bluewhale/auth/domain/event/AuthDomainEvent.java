package com.bluewhale.auth.domain.event;

/**
 * 认证权限域领域事件（AUTH-17 / T-DDD-11）。
 *
 * <p>以 {@code sealed} 接口收敛全部聚合事件类型，供应用层发布、基础设施层监听；
 * 领域层只描述「发生了什么」，不携带框架类型，也不感知缓存与日志实现。</p>
 *
 * @author BlueWhale
 */
public sealed interface AuthDomainEvent
        permits UserDomainEvent, RoleDomainEvent, PermissionDomainEvent, MenuDomainEvent,
        DeptDomainEvent, UserPermissionDomainEvent {

    /**
     * 聚合根标识
     *
     * @return 受影响聚合根的主键，新建且尚未回填主键时为 {@code null}
     */
    Long aggregateId();

    /**
     * 变更类型
     *
     * @return 本次变更的类型
     */
    ChangeType changeType();

    /**
     * 变更类型枚举
     *
     * @author BlueWhale
     */
    enum ChangeType {
        /** 新建 */
        CREATED,
        /** 更新 */
        UPDATED,
        /** 删除 */
        DELETED,
        /** 状态变更 */
        STATUS_CHANGED,
        /** 口令变更 */
        PASSWORD_CHANGED,
        /** 角色分配变更 */
        ROLES_ASSIGNED,
        /** 租户成员关系解除 */
        TENANT_UNBOUND,
        /** 权限分配变更 */
        PERMISSIONS_ASSIGNED,
        /** ABAC 属性全量覆盖 */
        ATTRS_REPLACED
    }
}

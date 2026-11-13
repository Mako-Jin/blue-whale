package com.bluewhale.auth.domain.event;

import com.bluewhale.common.domain.event.DomainEvent;

/**
 * 认证权限域领域事件（AUTH-17 / T-DDD-11）。
 *
 * <p>以 {@code sealed} 接口收敛全部聚合事件类型，供应用层发布、基础设施层监听；
 * 领域层只描述「发生了什么」，不携带框架类型，也不感知缓存与日志实现。</p>
 *
 * @author BlueWhale
 */
public sealed interface AuthDomainEvent extends DomainEvent
        permits UserDomainEvent, RoleDomainEvent, PermissionDomainEvent, MenuDomainEvent,
        DeptDomainEvent, UserPermissionDomainEvent, SocialBindingDomainEvent {

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
     * <p><b>语义边界</b>：{@link #UPDATED} 是「通用更新」兜底值，仅用于不被下列专用类型覆盖的
     * 一般字段变更（如改名、改资料）；凡有专用类型的变更（口令、状态、分配关系等）必须发布
     * 对应专用类型，不得混发 {@link #UPDATED}，消费方按精确类型匹配、不依赖兜底值。</p>
     *
     * @author BlueWhale
     */
    enum ChangeType {
        /** 新建聚合 */
        CREATED,
        /** 通用更新（兜底值：仅用于专用类型未覆盖的一般字段变更，见类级语义边界说明） */
        UPDATED,
        /** 删除聚合（逻辑删除） */
        DELETED,
        /** 启停状态变更（status 1/0 切换，不含用户停用） */
        STATUS_CHANGED,
        /** 口令变更（重置/自助修改，含强制首登改密后的改密） */
        PASSWORD_CHANGED,
        /** 用户-角色分配全量覆盖（{@code sys_user_role} 重建） */
        ROLES_ASSIGNED,
        /** 租户成员关系解除（用户退出/被移出租户） */
        TENANT_UNBOUND,
        /** 用户停用（与 {@link #STATUS_CHANGED} 区分：停用用户需额外撤销其全部存量令牌） */
        USER_DISABLED,
        /** 角色-权限分配全量覆盖（{@code sys_role_permission} 重建） */
        PERMISSIONS_ASSIGNED,
        /** 用户 ABAC 属性全量覆盖（{@code sys_user_permission} 重建） */
        ATTRS_REPLACED
    }
}

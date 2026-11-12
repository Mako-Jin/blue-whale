package com.bluewhale.auth.infrastructure.persistence.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户-租户投影：权限点变更跨租户失效缓存时，用于定位受影响用户及其角色所属租户。
 *
 * <p>权限点（{@code sys_permission}）为平台共享数据，一次权限点变更可能影响多个租户的
 * 角色与用户；由「权限 → 角色 → 用户」单条 JOIN SQL 直接带出角色所属租户，避免三次查询
 * 与内存组装（Wave 6.49）。</p>
 *
 * @author BlueWhale
 */
@Data
public class UserTenantRefPo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色所属租户 ID */
    private Long tenantId;

    /** 用户 ID */
    private Long userId;
}

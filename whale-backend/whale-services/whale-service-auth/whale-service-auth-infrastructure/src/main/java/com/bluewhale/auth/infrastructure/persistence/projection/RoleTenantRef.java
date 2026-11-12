package com.bluewhale.auth.infrastructure.persistence.projection;

import lombok.Data;

import java.io.Serializable;

/**
 * 角色-租户投影：跨租户缓存失效时用于定位受影响角色所属租户。
 *
 * <p>权限点（{@code sys_permission}）为平台共享数据，一次权限点变更可能影响多个租户的角色；
 * {@code sys_role} 本身按租户隔离，需绕过行级租户过滤按角色 ID 反查租户。</p>
 *
 * @author BlueWhale
 */
@Data
public class RoleTenantRef implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色 ID */
    private Long roleId;

    /** 角色所属租户 ID */
    private Long tenantId;
}

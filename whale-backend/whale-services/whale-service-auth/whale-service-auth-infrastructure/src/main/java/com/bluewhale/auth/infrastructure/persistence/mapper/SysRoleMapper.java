package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.projection.RoleTenantRef;
import com.bluewhale.auth.infrastructure.persistence.po.SysRolePo;
import com.bluewhale.auth.infrastructure.persistence.projection.UserTenantRef;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 系统角色 Mapper
 *
 * @author BlueWhale
 */
public interface SysRoleMapper extends BaseMapper<SysRolePo> {

    /**
     * 按角色 ID 批量反查角色所属租户（绕过行级租户过滤）。
     *
     * <p>权限点为平台共享数据，权限点变更需失效多个租户的用户缓存；角色表按租户隔离，
     * 此处仅用于失效路径定位 Key 所属租户，不参与业务查询。</p>
     *
     * @param roleIds 角色 ID 集合，调用方保证非空
     * @return 角色 ID → 租户 ID 投影
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT id AS roleId, tenant_id AS tenantId FROM sys_role WHERE id IN "
            + "<foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>#{roleId}</foreach>"
            + "</script>")
    List<RoleTenantRef> selectTenantRefsByIds(@Param("roleIds") Collection<Long> roleIds);

    /**
     * 单条 JOIN 反查持有指定权限点的全部（租户, 用户）对（绕过行级租户过滤）。
     *
     * <p>权限点变更需失效多个租户的用户权限缓存：权限 → 角色（带出所属租户）→ 用户
     * 一次查询完成，替代「三次查询 + 内存组装」，也不产生大 IN 参数列表。</p>
     *
     * @param permissionId 权限点 ID
     * @return （租户 ID, 用户 ID）对投影（DISTINCT 去重）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT DISTINCT r.tenant_id AS tenantId, ur.user_id AS userId "
            + "FROM sys_role_permission rp "
            + "JOIN sys_role r ON r.id = rp.role_id AND r.deleted = 0 "
            + "JOIN sys_user_role ur ON ur.role_id = rp.role_id "
            + "WHERE rp.permission_id = #{permissionId}")
    List<UserTenantRef> selectUserTenantRefsByPermissionId(@Param("permissionId") Long permissionId);
}

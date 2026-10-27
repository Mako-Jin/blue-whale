package com.bluewhale.auth.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.entity.RoleTenantRef;
import com.bluewhale.auth.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 系统角色 Mapper
 *
 * @author BlueWhale
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

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
}

package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserRolePo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 用户-角色关联 Mapper
 *
 * @author BlueWhale
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRolePo> {

    /**
     * 物理删除用户在指定租户内的角色关联行。
     *
     * <p>{@code sys_user_role} 为租户忽略表，但子查询引用租户隔离表 {@code sys_role}；
     * 显式关闭租户插件改写，避免平台上下文（tenant=1）给子查询追加 {@code tenant_id=1}
     * 后与目标租户条件冲突导致删除不到行。租户过滤条件由本语句显式给出。</p>
     *
     * @param userId 用户 ID
     * @param tenantId 目标租户 ID
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId} AND role_id IN "
            + "(SELECT id FROM sys_role WHERE tenant_id = #{tenantId})")
    int deleteByUserAndRoleTenant(@Param("userId") Long userId, @Param("tenantId") Long tenantId);
}

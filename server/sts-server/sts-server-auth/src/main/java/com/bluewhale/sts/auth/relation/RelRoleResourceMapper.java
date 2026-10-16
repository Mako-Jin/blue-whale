package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 角色-资源仓储。
 *
 * <p>与 {@code RoleMapper} 一样，有两个绕过 {@code @TableLogic} 的例外，
 * 场景却不同 —— 这里的软删行不是"被删掉的授权"，而是"角色被删除时一并撤掉的授权"
 * 留下的残骸（见 {@code RoleService.delete}）。当同一个角色被复活、又要把同一个资源
 * 授回去时，不复用那一行就会让表里出现两条只有 {@code deleted} 不同的记录 ——
 * 判定上无害（判定按资源聚合），但读表的人再也说不清"这个角色到底授过几次"。
 *
 * <p>{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。
 */
@Mapper
public interface RelRoleResourceMapper extends BaseMapper<RelRoleResourceEntity> {

    /**
     * 按角色 + 资源取**所有**匹配行（含已逻辑删除的），按主键升序。
     *
     * <p>用于授权的 upsert：先"看得见全部"，再决定是更新、复活还是新建。
     * 绕过的理由同 {@code RoleMapper.selectAnyByTenantAndCode} ——
     * MyBatis-Plus 会自动追加 {@code deleted = 0}，让常规查询对软删行完全失明。
     *
     * <p><b>返回 List 而不是单个实体，也不用 {@code LIMIT 1}：</b>
     * (role_id, resource_id) 上没有唯一索引，理论上可能出现重复行（并发授予时，
     * 服务层的"先查后写"挡不住竞态）。返回单实体会在这种情况抛
     * {@code TooManyResultsException}，把"数据重复"变成一次 500；
     * 而判定侧按资源聚合，重复行本来就是无害的。调用方取第一条即可 ——
     * 具体取舍见 {@code RoleService.grant}。
     *
     * <p>刻意不写 {@code LIMIT}：三套数据库对它的支持程度不同（达梦在 Oracle 兼容模式下
     * 更认 {@code ROWNUM}），而这条查询的结果集本来就极小，没有分页的必要。
     */
    @Select("""
            SELECT * FROM bw_auth_rel_role_resource
            WHERE role_id = #{roleId} AND resource_id = #{resourceId}
            ORDER BY id
            """)
    List<RelRoleResourceEntity> selectAnyByRoleAndResource(@Param("roleId") Long roleId,
                                                           @Param("resourceId") Long resourceId);

    /**
     * 复活一条被撤掉过的授权。{@code updateById} 对软删行影响 0 行且不报错，只能手写。
     *
     * <p><b>租户条件是这条 SQL 自己的事</b>（{@code decisions/0079}）：它绕过
     * {@code @TableLogic}，也就绕过了"先 {@code requireXxx} 再改"那条链上唯一的租户保证
     * （改前这一格只靠调用点：那一行是按"角色 + 资源"查出来的，角色已过可见性判定）。
     * 形状由 {@code scripts/verify-tenant-visibility.mjs} 的规则 F 守。
     */
    @Update("UPDATE bw_auth_rel_role_resource SET deleted = 0 WHERE id = #{id} AND tenant_id = #{tenantId}")
    int restoreDeletedById(@Param("id") Long id, @Param("tenantId") Long tenantId);

}

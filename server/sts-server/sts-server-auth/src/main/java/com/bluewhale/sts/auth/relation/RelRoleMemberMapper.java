package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 角色-成员仓储 —— 对应"某个角色被授给了谁"。
 *
 * <p>除继承来的通用 CRUD 外，本接口有两个**刻意绕过 {@code @TableLogic} 的例外**
 * （手写 SQL），与 {@code RelRoleResourceMapper} 是同一种场景：
 * 软删行不是"从未授过"，而是"解除过成员关系"留下的残骸。
 * 当同一个人被重新加回同一个角色时，不复用那一行就会让表里出现两条只有
 * {@code deleted} 不同的记录 —— 判定上无害（判定按角色取并集），
 * 但读表的人再也说不清"这个角色到底给过谁几次"。
 *
 * <p>{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。
 */
@Mapper
public interface RelRoleMemberMapper extends BaseMapper<RelRoleMemberEntity> {

    /**
     * 按 角色 + 成员类型 + 成员主键取**所有**匹配行（含已逻辑删除的），按主键升序。
     *
     * <p>用于成员关系的 upsert：先"看得见全部"，再决定是更新时间窗、复活还是新建。
     * 绕过的理由同 {@code RoleMapper.selectAnyByTenantAndCode} ——
     * MyBatis-Plus 会自动追加 {@code deleted = 0}，让常规查询对软删行完全失明。
     *
     * <p><b>{@code member_type} 必须参与定位，不能只用 {@code member_id}：</b>
     * 表上没有 (role_id, member_type, member_id) 唯一索引（见 {@code V3} 迁移脚本），
     * 而 {@code member_id} 只在它自己所指向的那张表里有意义 ——
     * {@code bw_auth_user} 与 {@code bw_auth_user_group} 是两个独立的自增序列，
     * 两个主键会撞号。少了这一列，"撤销给了 ID=5 的用户"会连带撤掉"ID=5 的用户组"。
     *
     * <p><b>返回 List 而不是单个实体，也不用 {@code LIMIT 1}：</b>
     * 与 {@code RelRoleResourceMapper} 同理 —— 没有唯一索引意味着并发下可能留下重复行，
     * 返回单实体会把"数据重复"变成一次 {@code TooManyResultsException}（500），
     * 而判定侧取并集，重复行本来就是无害的。调用方取第一条即可。
     */
    @Select("""
            SELECT * FROM bw_auth_rel_role_member
            WHERE role_id = #{roleId}
              AND member_type = #{memberType}
              AND member_id = #{memberId}
            ORDER BY id
            """)
    List<RelRoleMemberEntity> selectAnyByRoleAndMember(@Param("roleId") Long roleId,
                                                       @Param("memberType") String memberType,
                                                       @Param("memberId") Long memberId);

    /**
     * 复活一条被解除过的成员关系。{@code updateById} 对软删行影响 0 行且不报错，只能手写。
     *
     * <p><b>租户条件是这条 SQL 自己的事</b>（{@code decisions/0079}）：它绕过
     * {@code @TableLogic}，也就绕过了"先 {@code requireXxx} 再改"那条链上唯一的租户保证
     * （改前这一格只靠调用点：那一行是按"角色 + 成员"查出来的，角色已过可见性判定）。
     * 形状由 {@code scripts/verify-tenant-visibility.mjs} 的规则 F 守。
     */
    @Update("UPDATE bw_auth_rel_role_member SET deleted = 0 WHERE id = #{id} AND tenant_id = #{tenantId}")
    int restoreDeletedById(@Param("id") Long id, @Param("tenantId") Long tenantId);

}

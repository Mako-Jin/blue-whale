package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户组-成员仓储 —— 对应"这个用户组里有哪些人"。
 *
 * <p>除继承来的通用 CRUD 外，本接口有两个**刻意绕过 {@code @TableLogic} 的例外**
 * （手写 SQL），与 {@code RelRoleMemberMapper} / {@code RelRoleResourceMapper} 同源：
 * 软删行不是"从未加过"，而是"移除过成员"留下的残骸。把同一个人重新加回同一个组时，
 * 不复用那一行就会让表里出现两条只有 {@code deleted} 不同的记录 ——
 * 判定上无害（判定按组取并集），但读表的人再也说不清"这个组到底加过谁几次"。
 *
 * <p>{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。
 */
@Mapper
public interface RelUserGroupMemberMapper extends BaseMapper<RelUserGroupMemberEntity> {

    /**
     * 按 用户组 + 成员类型 + 成员主键取**所有**匹配行（含已逻辑删除的），按主键升序。
     *
     * <p>用于组成员关系的 upsert：先"看得见全部"，再决定是更新时间窗、复活还是新建。
     * 绕过的理由同 {@code RelRoleMemberMapper.selectAnyByRoleAndMember} ——
     * MyBatis-Plus 会自动追加 {@code deleted = 0}，让常规查询对软删行完全失明。
     *
     * <p><b>{@code member_type} 参与定位，不能只用 {@code member_id}：</b>
     * 这一列在当前数据下恒为 {@code USER}（组嵌套被接口显式拒绝，见
     * {@code MemberType} 的注释），但表是**多态**的、且历史上可能有直接写库产生的
     * {@code GROUP} 行。缺了它，"移除 ID=5 的用户"会连带移除"ID=5 的组"——
     * 而"移错了"表现为权限凭空消失，没有报错也没有痕迹。
     * 保留三段定位还有一个实际用途：将来若真补上嵌套，接口形状不必再改一次。
     *
     * <p><b>返回 List 而不是单个实体，也不用 {@code LIMIT 1}：</b>
     * 表上没有 (user_group_id, member_type, member_id) 唯一索引（见 {@code V3} 迁移脚本），
     * 并发下可能留下重复行。返回单实体会把"数据重复"变成一次
     * {@code TooManyResultsException}（500），而判定侧取并集、重复行本就无害。
     */
    @Select("""
            SELECT * FROM bw_auth_rel_user_group_member
            WHERE user_group_id = #{userGroupId}
              AND member_type = #{memberType}
              AND member_id = #{memberId}
            ORDER BY id
            """)
    List<RelUserGroupMemberEntity> selectAnyByGroupAndMember(@Param("userGroupId") Long userGroupId,
                                                             @Param("memberType") String memberType,
                                                             @Param("memberId") Long memberId);

    /** 复活一条被移除过的组成员关系。{@code updateById} 对软删行影响 0 行且不报错，只能手写 */
    @Update("UPDATE bw_auth_rel_user_group_member SET deleted = 0 WHERE id = #{id}")
    int restoreDeletedById(@Param("id") Long id);

}

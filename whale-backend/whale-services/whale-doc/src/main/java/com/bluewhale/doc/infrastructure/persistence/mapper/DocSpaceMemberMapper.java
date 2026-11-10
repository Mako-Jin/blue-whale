package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpaceMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 空间成员 Mapper。
 *
 * <p>成员移除为逻辑删除，但唯一键不含 {@code deleted}，因此重新邀请时必须复用原行。
 * 本接口的 {@link #selectAny} / {@link #reactivate} 显式包含逻辑删除行。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface DocSpaceMemberMapper extends BaseMapper<DocSpaceMember> {

    /**
     * 查询成员行（包含逻辑删除）。
     *
     * @param tenantId 租户ID
     * @param spaceId  空间ID
     * @param userId   用户ID
     * @return 成员行；不存在返回 {@code null}
     */
    @Select("""
            SELECT * FROM doc_space_member
            WHERE tenant_id = #{tenantId} AND space_id = #{spaceId} AND user_id = #{userId}
            LIMIT 1
            """)
    DocSpaceMember selectAny(@Param("tenantId") Long tenantId,
                             @Param("spaceId") Long spaceId,
                             @Param("userId") Long userId);

    /**
     * 复用已逻辑删除的成员行并更新角色。
     *
     * @param tenantId 租户ID
     * @param id       成员行ID
     * @param role     新角色
     * @return 受影响行数
     */
    @Update("""
            UPDATE doc_space_member
            SET deleted = 0, member_role = #{role}, update_time = NOW()
            WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    int reactivate(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("role") String role);
}

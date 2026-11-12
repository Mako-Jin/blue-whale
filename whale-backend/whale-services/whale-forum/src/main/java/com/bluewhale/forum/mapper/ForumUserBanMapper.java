package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumUserBan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 禁言/封号 Mapper。
 *
 * <p>生效判定只查 {@code status='ACTIVE'} 且未到期的记录，不做「取最新一条」——
 * 最新一条可能是已解除的旧记录，也可能是改期前的旧禁言。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumUserBanMapper extends BaseMapper<ForumUserBan> {

    /**
     * 查询用户当前生效的处置（禁言/封号取最严的一条）。
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param now 当前时间
     * @return 生效记录；无则 {@code null}
     */
    @Select("SELECT * FROM fm_user_ban WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND status = 'ACTIVE' AND deleted = 0 "
            + "AND (expire_time IS NULL OR expire_time > #{now}) "
            + "ORDER BY CASE ban_type WHEN 'BAN' THEN 0 ELSE 1 END, create_time DESC LIMIT 1")
    ForumUserBan selectActive(@Param("tenantId") Long tenantId,
                              @Param("userId") Long userId,
                              @Param("now") LocalDateTime now);

    /**
     * 解除用户全部生效处置（解禁/改期前先置 RELEASED，避免多条并存）。
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param operatorId 操作人
     * @return 影响行数
     */
    @org.apache.ibatis.annotations.Update("UPDATE fm_user_ban SET status = 'RELEASED', "
            + "operator_id = #{operatorId}, update_time = NOW() "
            + "WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND status = 'ACTIVE'")
    int releaseAll(@Param("tenantId") Long tenantId,
                   @Param("userId") Long userId,
                   @Param("operatorId") Long operatorId);
}

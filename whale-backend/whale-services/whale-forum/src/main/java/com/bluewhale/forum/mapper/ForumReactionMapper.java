package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumReaction;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 互动 Mapper（投花 / 藏帖）。
 *
 * <p>写入用 {@code INSERT IGNORE}、删除用 {@code DELETE}，都不走「先查后写」：
 * 唯一键 {@code uk_fm_reaction} 本身就是幂等判据，重复投花返回 0 行即视为已投过，
 * 不必靠抛 {@code DuplicateKeyException} 兜底——异常控制流会把事务标记成 rollback-only，
 * 后续计数更新就悄悄不再提交了。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumReactionMapper extends BaseMapper<ForumReaction> {

    /**
     * 幂等插入互动记录（已存在则忽略）。
     *
     * @param reaction 互动记录
     * @return 1 表示新插入；0 表示已存在
     */
    @Insert("INSERT IGNORE INTO fm_reaction "
            + "(id, tenant_id, user_id, target_type, target_id, reaction_type, create_time) "
            + "VALUES (#{id}, #{tenantId}, #{userId}, #{targetType}, #{targetId}, #{reactionType}, NOW())")
    int insertIgnore(ForumReaction reaction);

    /**
     * 物理删除互动记录（取消投花/藏帖）。
     *
     * @param userId 用户ID
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @param reactionType 互动类型
     * @return 删除行数（0 表示本来就没投过）
     */
    @Delete("DELETE FROM fm_reaction WHERE user_id = #{userId} AND target_type = #{targetType} "
            + "AND target_id = #{targetId} AND reaction_type = #{reactionType}")
    int deletePhysical(@Param("userId") Long userId,
                       @Param("targetType") String targetType,
                       @Param("targetId") Long targetId,
                       @Param("reactionType") String reactionType);
}

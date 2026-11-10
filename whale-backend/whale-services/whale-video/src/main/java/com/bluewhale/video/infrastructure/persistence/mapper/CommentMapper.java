package com.bluewhale.video.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.video.infrastructure.persistence.entity.ContentCommentEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 内容评论 Mapper（含点赞数原子更新）。
 *
 * @author BlueWhale
 */
public interface CommentMapper extends BaseMapper<ContentCommentEntity> {

    /**
     * 原子调整评论点赞数，结果不小于 0。
     *
     * @param commentId 评论 ID
     * @param delta 增量（+1 / -1）
     * @return 影响行数
     */
    @Update("UPDATE vd_comment SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{commentId}")
    int addLikeCount(@Param("commentId") Long commentId, @Param("delta") long delta);
}

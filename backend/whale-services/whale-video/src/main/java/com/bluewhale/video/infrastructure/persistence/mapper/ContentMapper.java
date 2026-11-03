package com.bluewhale.video.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.video.infrastructure.persistence.entity.ContentEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 内容 Mapper（含点赞 / 收藏 / 评论 / 播放计数的原子更新）。
 *
 * @author BlueWhale
 */
public interface ContentMapper extends BaseMapper<ContentEntity> {

    /**
     * 原子调整点赞数，结果不小于 0。
     *
     * @param contentId 内容 ID
     * @param delta 增量（+1 / -1）
     * @return 影响行数
     */
    @Update("UPDATE vd_content SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{contentId}")
    int addLikeCount(@Param("contentId") Long contentId, @Param("delta") long delta);

    /**
     * 原子调整收藏数，结果不小于 0。
     *
     * @param contentId 内容 ID
     * @param delta 增量（+1 / -1）
     * @return 影响行数
     */
    @Update("UPDATE vd_content SET favorite_count = GREATEST(favorite_count + #{delta}, 0) WHERE id = #{contentId}")
    int addFavoriteCount(@Param("contentId") Long contentId, @Param("delta") long delta);

    /**
     * 原子调整评论数，结果不小于 0（仅统计可见评论）。
     *
     * @param contentId 内容 ID
     * @param delta 增量（+1 / -1）
     * @return 影响行数
     */
    @Update("UPDATE vd_content SET comment_count = GREATEST(comment_count + #{delta}, 0) WHERE id = #{contentId}")
    int addCommentCount(@Param("contentId") Long contentId, @Param("delta") long delta);

    /**
     * 原子累加播放次数（Redis 增量定时回刷）。
     *
     * @param contentId 内容 ID
     * @param delta 增量
     * @return 影响行数
     */
    @Update("UPDATE vd_content SET play_count = play_count + #{delta} WHERE id = #{contentId}")
    int addPlayCount(@Param("contentId") Long contentId, @Param("delta") long delta);

    /**
     * 汇总全部未删除内容的播放次数（运营统计用）。
     *
     * @return 播放次数总和
     */
    @Select("SELECT COALESCE(SUM(play_count), 0) FROM vd_content WHERE deleted = 0")
    long sumPlayCount();
}

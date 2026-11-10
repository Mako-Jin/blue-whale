package com.bluewhale.music.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.music.infrastructure.persistence.entity.SongEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 歌曲 Mapper。
 *
 * @author BlueWhale
 */
public interface SongMapper extends BaseMapper<SongEntity> {

    /**
     * 原子调整收藏数，结果不小于 0（避免高并发下的读改写与负数）。
     *
     * @param songId 歌曲 ID
     * @param delta 增量（+1 / -1）
     * @return 影响行数
     */
    @Update("UPDATE mu_song SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{songId}")
    int addLikeCount(@Param("songId") Long songId, @Param("delta") long delta);

    /**
     * 原子累加播放次数（Redis 增量定时回刷）。
     *
     * @param songId 歌曲 ID
     * @param delta 增量
     * @return 影响行数
     */
    @Update("UPDATE mu_song SET play_count = play_count + #{delta} WHERE id = #{songId}")
    int addPlayCount(@Param("songId") Long songId, @Param("delta") long delta);

    /**
     * 原子调整评论数，结果不小于 0（仅统计可见评论）。
     *
     * @param songId 歌曲 ID
     * @param delta 增量（+1 / -1）
     * @return 影响行数
     */
    @Update("UPDATE mu_song SET comment_count = GREATEST(comment_count + #{delta}, 0) WHERE id = #{songId}")
    int addCommentCount(@Param("songId") Long songId, @Param("delta") long delta);
}

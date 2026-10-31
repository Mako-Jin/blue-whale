package com.bluewhale.music.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.music.domain.model.SongPlayRank;
import com.bluewhale.music.infrastructure.persistence.entity.PlayHistoryEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 播放历史 Mapper。
 *
 * <p>近 N 天热度排行使用注解 SQL 聚合；{@code tenant_id} 由 starter 的
 * {@code TenantLineInnerInterceptor} 对参与表自动追加，此处不显式写租户列。</p>
 *
 * @author BlueWhale
 */
public interface PlayHistoryMapper extends BaseMapper<PlayHistoryEntity> {

    /**
     * 近 N 天播放热度排行：按歌曲去重计数，仅统计当前上架且公开、版权未下架的歌曲。
     *
     * @param since 窗口起点（含）
     * @param offset 偏移量
     * @param limit 条数
     * @return 排行项（播放次数倒序）
     */
    @Select("SELECT h.song_id AS songId, COUNT(*) AS playCount FROM mu_play_history h "
            + "JOIN mu_song s ON s.id = h.song_id "
            + "WHERE h.played_at >= #{since} AND s.deleted = 0 "
            + "AND s.publish_status = 'PUBLISHED' AND s.visibility = 'PUBLIC' "
            + "AND s.copyright_status <> 'TAKEDOWN' "
            + "GROUP BY h.song_id ORDER BY COUNT(*) DESC, h.song_id ASC "
            + "LIMIT #{offset}, #{limit}")
    List<SongPlayRank> selectRankSince(@Param("since") LocalDateTime since,
                                       @Param("offset") long offset,
                                       @Param("limit") long limit);

    /**
     * 统计近 N 天有播放记录的上架公开歌曲数（排行总数）。
     *
     * @param since 窗口起点（含）
     * @return 歌曲数（按歌曲去重）
     */
    @Select("SELECT COUNT(DISTINCT h.song_id) FROM mu_play_history h "
            + "JOIN mu_song s ON s.id = h.song_id "
            + "WHERE h.played_at >= #{since} AND s.deleted = 0 "
            + "AND s.publish_status = 'PUBLISHED' AND s.visibility = 'PUBLIC' "
            + "AND s.copyright_status <> 'TAKEDOWN'")
    long countRankedSince(@Param("since") LocalDateTime since);
}

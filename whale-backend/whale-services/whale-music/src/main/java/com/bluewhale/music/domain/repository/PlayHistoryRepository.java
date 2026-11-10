package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.PlayHistory;
import com.bluewhale.music.domain.model.SongPlayRank;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 播放历史仓储端口。
 *
 * @author BlueWhale
 */
public interface PlayHistoryRepository {

    /**
     * 保存播放进度：同一用户同一歌曲只保留一条记录（存在则更新进度与播放时间）。
     *
     * @param history 播放历史
     */
    void saveProgress(PlayHistory history);

    /**
     * 分页查询用户最近播放（按播放时间倒序，每首歌一条）。
     *
     * @param userId 用户 ID
     * @param offset 偏移量
     * @param limit 条数
     * @return 播放历史列表
     */
    List<PlayHistory> page(Long userId, long offset, long limit);

    /**
     * 统计用户播放历史条数。
     *
     * @param userId 用户 ID
     * @return 总数
     */
    long countByUser(Long userId);

    /**
     * 清理早于指定时间的播放历史（保留期 90 天）。
     *
     * @param before 截止时间（不含）
     * @return 删除行数
     */
    int purgeBefore(LocalDateTime before);

    /**
     * 近 N 天播放热度排行（仅统计当前上架且公开、版权未下架的歌曲）。
     *
     * @param since 窗口起点（含）
     * @param offset 偏移量
     * @param limit 条数
     * @return 排行项（按窗口内播放次数倒序）
     */
    List<SongPlayRank> rankSince(LocalDateTime since, long offset, long limit);

    /**
     * 统计近 N 天有播放记录的上架公开歌曲数（排行总数）。
     *
     * @param since 窗口起点（含）
     * @return 歌曲数（按歌曲去重）
     */
    long countRankedSince(LocalDateTime since);
}

package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.PlayHistory;

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
}

package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.WatchHistory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 观看历史仓储端口（用户 + 内容唯一，重复上报更新进度）。
 *
 * @author BlueWhale
 */
public interface WatchHistoryRepository {

    /**
     * 保存进度：存在则更新进度与最近观看时间，不存在则新增。
     *
     * @param history 观看历史
     */
    void saveProgress(WatchHistory history);

    /**
     * 查询用户对指定内容的观看记录。
     *
     * @param userId 用户 ID
     * @param contentId 内容 ID
     * @return 观看历史；未观看返回空
     */
    Optional<WatchHistory> find(Long userId, Long contentId);

    /**
     * 分页查询用户最近观看记录（按最近观看时间倒序）。
     *
     * @param userId 用户 ID
     * @param offset 偏移
     * @param limit 条数
     * @return 观看历史列表
     */
    List<WatchHistory> pageByUser(Long userId, long offset, long limit);

    /**
     * 统计用户观看历史条数。
     *
     * @param userId 用户 ID
     * @return 总数
     */
    long countByUser(Long userId);

    /**
     * 删除用户对指定内容的观看记录（幂等）。
     *
     * @param userId 用户 ID
     * @param contentId 内容 ID
     */
    void delete(Long userId, Long contentId);

    /**
     * 按最近观看时间清理过期记录（单批）。
     *
     * <p>M5-7 保留期清理：早于 {@code cutoff} 的记录按批次删除，返回本批实际删除条数；
     * 后台任务无租户上下文时由租户插件按库内全部租户处理，多实例重复执行幂等。</p>
     *
     * @param cutoff 截止时间（早于该时间的记录被清理）
     * @param limit 单批最多删除条数（须大于 0）
     * @return 本批实际删除条数
     */
    int deleteWatchedBefore(LocalDateTime cutoff, int limit);
}

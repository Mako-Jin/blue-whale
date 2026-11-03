package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.video.domain.model.WatchHistory;
import com.bluewhale.video.domain.repository.WatchHistoryRepository;
import com.bluewhale.video.infrastructure.persistence.entity.WatchHistoryEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.WatchHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 观看历史仓储实现（每用户每内容一条，重复上报更新进度）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class WatchHistoryRepositoryImpl implements WatchHistoryRepository {

    private final WatchHistoryMapper watchHistoryMapper;

    @Override
    public void saveProgress(WatchHistory history) {
        WatchHistoryEntity existing = watchHistoryMapper.selectOne(new LambdaQueryWrapper<WatchHistoryEntity>()
                .eq(WatchHistoryEntity::getUserId, history.getUserId())
                .eq(WatchHistoryEntity::getContentId, history.getContentId())
                .orderByDesc(WatchHistoryEntity::getId)
                .last("LIMIT 1"));
        if (existing == null) {
            WatchHistoryEntity entity = new WatchHistoryEntity();
            entity.setUserId(history.getUserId());
            entity.setContentId(history.getContentId());
            entity.setProgressMs(history.getProgressMs() == null ? 0L : history.getProgressMs());
            entity.setDurationMs(history.getDurationMs() == null ? 0L : history.getDurationMs());
            entity.setSource(history.getSource());
            entity.setDevice(history.getDevice());
            entity.setWatchedAt(history.getWatchedAt());
            watchHistoryMapper.insert(entity);
            history.setId(entity.getId());
            return;
        }
        existing.setProgressMs(history.getProgressMs() == null ? 0L : history.getProgressMs());
        existing.setDurationMs(history.getDurationMs() == null ? 0L : history.getDurationMs());
        existing.setSource(history.getSource());
        existing.setDevice(history.getDevice());
        existing.setWatchedAt(history.getWatchedAt());
        watchHistoryMapper.updateById(existing);
        history.setId(existing.getId());
    }

    @Override
    public Optional<WatchHistory> find(Long userId, Long contentId) {
        return Optional.ofNullable(watchHistoryMapper.selectOne(new LambdaQueryWrapper<WatchHistoryEntity>()
                .eq(WatchHistoryEntity::getUserId, userId)
                .eq(WatchHistoryEntity::getContentId, contentId)
                .orderByDesc(WatchHistoryEntity::getId)
                .last("LIMIT 1"))).map(this::toDomain);
    }

    @Override
    public List<WatchHistory> pageByUser(Long userId, long offset, long limit) {
        long size = Math.max(limit, 1L);
        Page<WatchHistoryEntity> result = watchHistoryMapper.selectPage(new Page<>(offset / size + 1L, size),
                new LambdaQueryWrapper<WatchHistoryEntity>()
                        .eq(WatchHistoryEntity::getUserId, userId)
                        .orderByDesc(WatchHistoryEntity::getWatchedAt)
                        .orderByDesc(WatchHistoryEntity::getId));
        return result.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countByUser(Long userId) {
        return watchHistoryMapper.selectCount(new LambdaQueryWrapper<WatchHistoryEntity>()
                .eq(WatchHistoryEntity::getUserId, userId));
    }

    @Override
    public void delete(Long userId, Long contentId) {
        watchHistoryMapper.delete(new LambdaQueryWrapper<WatchHistoryEntity>()
                .eq(WatchHistoryEntity::getUserId, userId)
                .eq(WatchHistoryEntity::getContentId, contentId));
    }

    @Override
    public int deleteWatchedBefore(LocalDateTime cutoff, int limit) {
        if (cutoff == null || limit <= 0) {
            return 0;
        }
        List<Long> ids = watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistoryEntity>()
                        .select(WatchHistoryEntity::getId)
                        .lt(WatchHistoryEntity::getWatchedAt, cutoff)
                        .orderByAsc(WatchHistoryEntity::getWatchedAt)
                        .orderByAsc(WatchHistoryEntity::getId)
                        .last("LIMIT " + limit))
                .stream().map(WatchHistoryEntity::getId).toList();
        if (ids.isEmpty()) {
            return 0;
        }
        return watchHistoryMapper.delete(new LambdaQueryWrapper<WatchHistoryEntity>()
                .in(WatchHistoryEntity::getId, ids));
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 观看历史
     */
    private WatchHistory toDomain(WatchHistoryEntity entity) {
        WatchHistory history = new WatchHistory();
        history.setId(entity.getId());
        history.setUserId(entity.getUserId());
        history.setContentId(entity.getContentId());
        history.setProgressMs(entity.getProgressMs() == null ? 0L : entity.getProgressMs());
        history.setDurationMs(entity.getDurationMs() == null ? 0L : entity.getDurationMs());
        history.setSource(entity.getSource());
        history.setDevice(entity.getDevice());
        history.setWatchedAt(entity.getWatchedAt());
        return history;
    }
}

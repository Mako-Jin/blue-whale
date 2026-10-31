package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.model.PlayHistory;
import com.bluewhale.music.domain.repository.PlayHistoryRepository;
import com.bluewhale.music.infrastructure.persistence.entity.PlayHistoryEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.PlayHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 播放历史仓储实现（每用户每歌曲一条，重复上报更新进度）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PlayHistoryRepositoryImpl implements PlayHistoryRepository {

    private final PlayHistoryMapper playHistoryMapper;

    @Override
    public void saveProgress(PlayHistory history) {
        PlayHistoryEntity existing = playHistoryMapper.selectOne(new LambdaQueryWrapper<PlayHistoryEntity>()
                .eq(PlayHistoryEntity::getUserId, history.getUserId())
                .eq(PlayHistoryEntity::getSongId, history.getSongId())
                .orderByDesc(PlayHistoryEntity::getPlayedAt)
                .last("LIMIT 1"));
        if (existing == null) {
            PlayHistoryEntity entity = new PlayHistoryEntity();
            entity.setUserId(history.getUserId());
            entity.setSongId(history.getSongId());
            entity.setProgressMs(history.getProgressMs());
            entity.setSource(history.getSource());
            entity.setDevice(history.getDevice());
            entity.setPlayedAt(history.getPlayedAt());
            playHistoryMapper.insert(entity);
            history.setId(entity.getId());
            return;
        }
        existing.setProgressMs(history.getProgressMs());
        existing.setSource(history.getSource());
        existing.setDevice(history.getDevice());
        existing.setPlayedAt(history.getPlayedAt());
        playHistoryMapper.updateById(existing);
        history.setId(existing.getId());
    }

    @Override
    public List<PlayHistory> page(Long userId, long offset, long limit) {
        long pageNo = limit <= 0 ? 1L : offset / limit + 1L;
        Page<PlayHistoryEntity> result = playHistoryMapper.selectPage(new Page<>(pageNo, limit),
                new LambdaQueryWrapper<PlayHistoryEntity>()
                        .eq(PlayHistoryEntity::getUserId, userId)
                        .orderByDesc(PlayHistoryEntity::getPlayedAt)
                        .orderByDesc(PlayHistoryEntity::getId));
        return result.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countByUser(Long userId) {
        return playHistoryMapper.selectCount(new LambdaQueryWrapper<PlayHistoryEntity>()
                .eq(PlayHistoryEntity::getUserId, userId));
    }

    @Override
    public int purgeBefore(LocalDateTime before) {
        return playHistoryMapper.delete(new LambdaQueryWrapper<PlayHistoryEntity>()
                .lt(PlayHistoryEntity::getPlayedAt, before));
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 播放历史
     */
    private PlayHistory toDomain(PlayHistoryEntity entity) {
        PlayHistory history = new PlayHistory();
        history.setId(entity.getId());
        history.setUserId(entity.getUserId());
        history.setSongId(entity.getSongId());
        history.setProgressMs(entity.getProgressMs() == null ? 0L : entity.getProgressMs());
        history.setSource(entity.getSource());
        history.setDevice(entity.getDevice());
        history.setPlayedAt(entity.getPlayedAt());
        return history;
    }
}

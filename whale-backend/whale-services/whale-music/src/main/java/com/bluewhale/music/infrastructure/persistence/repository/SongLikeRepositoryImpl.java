package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.repository.SongLikeRepository;
import com.bluewhale.music.infrastructure.persistence.entity.SongLikeEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.SongLikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 歌曲收藏仓储实现（唯一键 + 先查后写保证幂等）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SongLikeRepositoryImpl implements SongLikeRepository {

    private final SongLikeMapper songLikeMapper;

    @Override
    public boolean add(Long userId, Long songId) {
        if (exists(userId, songId)) {
            return false;
        }
        SongLikeEntity entity = new SongLikeEntity();
        entity.setUserId(userId);
        entity.setSongId(songId);
        songLikeMapper.insert(entity);
        return true;
    }

    @Override
    public boolean remove(Long userId, Long songId) {
        return songLikeMapper.delete(new LambdaQueryWrapper<SongLikeEntity>()
                .eq(SongLikeEntity::getUserId, userId)
                .eq(SongLikeEntity::getSongId, songId)) > 0;
    }

    @Override
    public boolean exists(Long userId, Long songId) {
        return songLikeMapper.selectCount(new LambdaQueryWrapper<SongLikeEntity>()
                .eq(SongLikeEntity::getUserId, userId)
                .eq(SongLikeEntity::getSongId, songId)) > 0;
    }

    @Override
    public List<Long> pageSongIds(Long userId, long offset, long limit) {
        long pageNo = limit <= 0 ? 1L : offset / limit + 1L;
        Page<SongLikeEntity> result = songLikeMapper.selectPage(new Page<>(pageNo, limit),
                new LambdaQueryWrapper<SongLikeEntity>()
                        .eq(SongLikeEntity::getUserId, userId)
                        .orderByDesc(SongLikeEntity::getId));
        return result.getRecords().stream().map(SongLikeEntity::getSongId).toList();
    }

    @Override
    public long countByUser(Long userId) {
        return songLikeMapper.selectCount(new LambdaQueryWrapper<SongLikeEntity>()
                .eq(SongLikeEntity::getUserId, userId));
    }
}

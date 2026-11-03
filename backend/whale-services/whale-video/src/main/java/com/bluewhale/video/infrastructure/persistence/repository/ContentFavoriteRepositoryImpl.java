package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.video.domain.repository.ContentFavoriteRepository;
import com.bluewhale.video.infrastructure.persistence.entity.ContentFavoriteEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.ContentFavoriteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 内容收藏仓储实现（唯一键 + 先查后写保证幂等）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ContentFavoriteRepositoryImpl implements ContentFavoriteRepository {

    private final ContentFavoriteMapper contentFavoriteMapper;

    @Override
    public boolean add(Long userId, Long contentId) {
        if (exists(userId, contentId)) {
            return false;
        }
        ContentFavoriteEntity entity = new ContentFavoriteEntity();
        entity.setUserId(userId);
        entity.setContentId(contentId);
        contentFavoriteMapper.insert(entity);
        return true;
    }

    @Override
    public boolean remove(Long userId, Long contentId) {
        return contentFavoriteMapper.delete(new LambdaQueryWrapper<ContentFavoriteEntity>()
                .eq(ContentFavoriteEntity::getUserId, userId)
                .eq(ContentFavoriteEntity::getContentId, contentId)) > 0;
    }

    @Override
    public boolean exists(Long userId, Long contentId) {
        return contentFavoriteMapper.selectCount(new LambdaQueryWrapper<ContentFavoriteEntity>()
                .eq(ContentFavoriteEntity::getUserId, userId)
                .eq(ContentFavoriteEntity::getContentId, contentId)) > 0;
    }

    @Override
    public List<Long> pageContentIds(Long userId, long offset, long limit) {
        long size = Math.max(limit, 1L);
        Page<ContentFavoriteEntity> result = contentFavoriteMapper.selectPage(new Page<>(offset / size + 1L, size),
                new LambdaQueryWrapper<ContentFavoriteEntity>()
                        .eq(ContentFavoriteEntity::getUserId, userId)
                        .orderByDesc(ContentFavoriteEntity::getId));
        return result.getRecords().stream().map(ContentFavoriteEntity::getContentId).toList();
    }

    @Override
    public long countByUser(Long userId) {
        return contentFavoriteMapper.selectCount(new LambdaQueryWrapper<ContentFavoriteEntity>()
                .eq(ContentFavoriteEntity::getUserId, userId));
    }
}

package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.video.domain.repository.ContentLikeRepository;
import com.bluewhale.video.infrastructure.persistence.entity.ContentLikeEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.ContentLikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 内容点赞仓储实现（唯一键 + 先查后写保证幂等）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ContentLikeRepositoryImpl implements ContentLikeRepository {

    private final ContentLikeMapper contentLikeMapper;

    @Override
    public boolean add(Long userId, Long contentId) {
        if (exists(userId, contentId)) {
            return false;
        }
        ContentLikeEntity entity = new ContentLikeEntity();
        entity.setUserId(userId);
        entity.setContentId(contentId);
        contentLikeMapper.insert(entity);
        return true;
    }

    @Override
    public boolean remove(Long userId, Long contentId) {
        return contentLikeMapper.delete(new LambdaQueryWrapper<ContentLikeEntity>()
                .eq(ContentLikeEntity::getUserId, userId)
                .eq(ContentLikeEntity::getContentId, contentId)) > 0;
    }

    @Override
    public boolean exists(Long userId, Long contentId) {
        return contentLikeMapper.selectCount(new LambdaQueryWrapper<ContentLikeEntity>()
                .eq(ContentLikeEntity::getUserId, userId)
                .eq(ContentLikeEntity::getContentId, contentId)) > 0;
    }

    @Override
    public List<Long> pageContentIds(Long userId, long offset, long limit) {
        long size = Math.max(limit, 1L);
        Page<ContentLikeEntity> result = contentLikeMapper.selectPage(new Page<>(offset / size + 1L, size),
                new LambdaQueryWrapper<ContentLikeEntity>()
                        .eq(ContentLikeEntity::getUserId, userId)
                        .orderByDesc(ContentLikeEntity::getId));
        return result.getRecords().stream().map(ContentLikeEntity::getContentId).toList();
    }

    @Override
    public long countByUser(Long userId) {
        return contentLikeMapper.selectCount(new LambdaQueryWrapper<ContentLikeEntity>()
                .eq(ContentLikeEntity::getUserId, userId));
    }
}

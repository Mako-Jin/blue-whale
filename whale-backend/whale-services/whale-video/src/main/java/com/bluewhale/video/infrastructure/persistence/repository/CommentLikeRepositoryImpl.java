package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.video.domain.repository.CommentLikeRepository;
import com.bluewhale.video.infrastructure.persistence.entity.CommentLikeEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.CommentLikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 评论点赞仓储实现（唯一键 + 先查后写保证幂等）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CommentLikeRepositoryImpl implements CommentLikeRepository {

    private final CommentLikeMapper commentLikeMapper;

    @Override
    public boolean add(Long userId, Long commentId) {
        boolean exists = commentLikeMapper.selectCount(new LambdaQueryWrapper<CommentLikeEntity>()
                .eq(CommentLikeEntity::getUserId, userId)
                .eq(CommentLikeEntity::getCommentId, commentId)) > 0;
        if (exists) {
            return false;
        }
        CommentLikeEntity entity = new CommentLikeEntity();
        entity.setUserId(userId);
        entity.setCommentId(commentId);
        commentLikeMapper.insert(entity);
        return true;
    }

    @Override
    public boolean remove(Long userId, Long commentId) {
        return commentLikeMapper.delete(new LambdaQueryWrapper<CommentLikeEntity>()
                .eq(CommentLikeEntity::getUserId, userId)
                .eq(CommentLikeEntity::getCommentId, commentId)) > 0;
    }

    @Override
    public List<Long> findLiked(Long userId, Collection<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLikeEntity>()
                        .eq(CommentLikeEntity::getUserId, userId)
                        .in(CommentLikeEntity::getCommentId, commentIds))
                .stream().map(CommentLikeEntity::getCommentId).toList();
    }
}

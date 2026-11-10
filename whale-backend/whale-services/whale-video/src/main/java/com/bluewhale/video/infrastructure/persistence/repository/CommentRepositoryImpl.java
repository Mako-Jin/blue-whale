package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.video.domain.model.CommentStatus;
import com.bluewhale.video.domain.model.ContentComment;
import com.bluewhale.video.domain.repository.CommentRepository;
import com.bluewhale.video.infrastructure.persistence.entity.ContentCommentEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.CommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 内容评论仓储实现（MyBatis-Plus，逻辑删除 + 原子点赞数）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepository {

    /** 顶级评论的父 ID */
    private static final long TOP_LEVEL_PARENT_ID = 0L;

    private final CommentMapper commentMapper;

    @Override
    public void insert(ContentComment comment) {
        ContentCommentEntity entity = new ContentCommentEntity();
        entity.setContentId(comment.getContentId());
        entity.setUserId(comment.getUserId());
        entity.setParentId(comment.getParentId() == null ? TOP_LEVEL_PARENT_ID : comment.getParentId());
        entity.setContent(comment.getContent());
        entity.setStatus(comment.getStatus() == null ? CommentStatus.VISIBLE.name() : comment.getStatus().name());
        entity.setLikeCount(comment.getLikeCount() == null ? 0 : comment.getLikeCount());
        commentMapper.insert(entity);
        comment.setId(entity.getId());
        comment.setCreateTime(entity.getCreateTime());
    }

    @Override
    public Optional<ContentComment> findById(Long id) {
        return Optional.ofNullable(commentMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<ContentComment> pageVisibleRoots(Long contentId, long offset, long limit) {
        return commentMapper.selectPage(page(offset, limit), new LambdaQueryWrapper<ContentCommentEntity>()
                        .eq(ContentCommentEntity::getContentId, contentId)
                        .eq(ContentCommentEntity::getParentId, TOP_LEVEL_PARENT_ID)
                        .eq(ContentCommentEntity::getStatus, CommentStatus.VISIBLE.name())
                        .orderByDesc(ContentCommentEntity::getCreateTime)
                        .orderByDesc(ContentCommentEntity::getId))
                .getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public List<ContentComment> findVisibleReplies(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return commentMapper.selectList(new LambdaQueryWrapper<ContentCommentEntity>()
                        .in(ContentCommentEntity::getParentId, parentIds)
                        .eq(ContentCommentEntity::getStatus, CommentStatus.VISIBLE.name())
                        .orderByAsc(ContentCommentEntity::getCreateTime)
                        .orderByAsc(ContentCommentEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countVisible(Long contentId) {
        return commentMapper.selectCount(new LambdaQueryWrapper<ContentCommentEntity>()
                .eq(ContentCommentEntity::getContentId, contentId)
                .eq(ContentCommentEntity::getStatus, CommentStatus.VISIBLE.name()));
    }

    @Override
    public List<ContentComment> pageByStatus(CommentStatus status, long offset, long limit) {
        return commentMapper.selectPage(page(offset, limit), new LambdaQueryWrapper<ContentCommentEntity>()
                        .eq(ContentCommentEntity::getStatus, status.name())
                        .orderByAsc(ContentCommentEntity::getCreateTime)
                        .orderByAsc(ContentCommentEntity::getId))
                .getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countByStatus(CommentStatus status) {
        return commentMapper.selectCount(new LambdaQueryWrapper<ContentCommentEntity>()
                .eq(ContentCommentEntity::getStatus, status.name()));
    }

    @Override
    public int updateStatus(Long id, CommentStatus status) {
        return commentMapper.update(null, new LambdaUpdateWrapper<ContentCommentEntity>()
                .eq(ContentCommentEntity::getId, id)
                .set(ContentCommentEntity::getStatus, status.name()));
    }

    @Override
    public void delete(Long id) {
        commentMapper.deleteById(id);
    }

    @Override
    public void addLikeCount(Long commentId, long delta) {
        commentMapper.addLikeCount(commentId, delta);
    }

    /**
     * 构造分页参数。
     *
     * @param offset 偏移
     * @param limit 条数
     * @return 分页参数
     */
    private Page<ContentCommentEntity> page(long offset, long limit) {
        long size = Math.max(limit, 1L);
        return new Page<>(offset / size + 1L, size);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 评论
     */
    private ContentComment toDomain(ContentCommentEntity entity) {
        ContentComment comment = new ContentComment();
        comment.setId(entity.getId());
        comment.setContentId(entity.getContentId());
        comment.setUserId(entity.getUserId());
        comment.setParentId(entity.getParentId() == null ? TOP_LEVEL_PARENT_ID : entity.getParentId());
        comment.setContent(entity.getContent());
        comment.setStatus(CommentStatus.parse(entity.getStatus()));
        comment.setLikeCount(entity.getLikeCount() == null ? 0 : entity.getLikeCount());
        comment.setCreateTime(entity.getCreateTime());
        return comment;
    }
}

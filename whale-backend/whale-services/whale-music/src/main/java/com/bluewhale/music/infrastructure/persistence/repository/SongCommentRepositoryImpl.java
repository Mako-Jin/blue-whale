package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.model.CommentStatus;
import com.bluewhale.music.domain.model.SongComment;
import com.bluewhale.music.domain.repository.CommentRepository;
import com.bluewhale.music.infrastructure.persistence.entity.SongCommentEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.SongCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 歌曲评论仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SongCommentRepositoryImpl implements CommentRepository {

    private final SongCommentMapper songCommentMapper;

    @Override
    public Optional<SongComment> findById(Long id) {
        return Optional.ofNullable(songCommentMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void insert(SongComment comment) {
        SongCommentEntity entity = new SongCommentEntity();
        BeanUtils.copyProperties(comment, entity);
        entity.setStatus(comment.getStatus().name());
        songCommentMapper.insert(entity);
        comment.setId(entity.getId());
        comment.setCreateTime(entity.getCreateTime());
        comment.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(SongComment comment) {
        SongCommentEntity entity = new SongCommentEntity();
        BeanUtils.copyProperties(comment, entity);
        entity.setStatus(comment.getStatus().name());
        songCommentMapper.updateById(entity);
    }

    @Override
    public List<SongComment> pageTopLevel(Long songId, long offset, long limit) {
        long pageNo = limit <= 0 ? 1L : offset / limit + 1L;
        Page<SongCommentEntity> result = songCommentMapper.selectPage(new Page<>(pageNo, limit),
                visibleWrapper(songId)
                        .eq(SongCommentEntity::getParentId, SongComment.TOP_LEVEL_PARENT_ID)
                        .orderByDesc(SongCommentEntity::getCreateTime));
        return result.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countTopLevel(Long songId) {
        return songCommentMapper.selectCount(visibleWrapper(songId)
                .eq(SongCommentEntity::getParentId, SongComment.TOP_LEVEL_PARENT_ID));
    }

    @Override
    public List<SongComment> findReplies(Long songId, Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return List.of();
        }
        return songCommentMapper.selectList(visibleWrapper(songId)
                        .in(SongCommentEntity::getParentId, parentIds)
                        .orderByAsc(SongCommentEntity::getCreateTime))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<SongComment> pageAudit(Long songId, CommentStatus status, String keyword,
                                       long offset, long limit) {
        long pageNo = limit <= 0 ? 1L : offset / limit + 1L;
        Page<SongCommentEntity> result = songCommentMapper.selectPage(new Page<>(pageNo, limit),
                auditWrapper(songId, status, keyword));
        return result.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countAudit(Long songId, CommentStatus status, String keyword) {
        return songCommentMapper.selectCount(auditWrapper(songId, status, keyword));
    }

    /**
     * 构造用户侧可见评论查询条件。
     *
     * @param songId 歌曲 ID
     * @return 查询条件
     */
    private LambdaQueryWrapper<SongCommentEntity> visibleWrapper(Long songId) {
        return new LambdaQueryWrapper<SongCommentEntity>()
                .eq(SongCommentEntity::getSongId, songId)
                .eq(SongCommentEntity::getStatus, CommentStatus.VISIBLE.name());
    }

    /**
     * 构造运营审核查询条件（倒序，状态 / 歌曲 / 关键词可组合过滤）。
     *
     * @param songId 歌曲 ID，可为空
     * @param status 状态，可为空
     * @param keyword 正文关键词，可为空
     * @return 查询条件
     */
    private LambdaQueryWrapper<SongCommentEntity> auditWrapper(Long songId, CommentStatus status,
                                                               String keyword) {
        LambdaQueryWrapper<SongCommentEntity> wrapper = new LambdaQueryWrapper<>();
        if (songId != null) {
            wrapper.eq(SongCommentEntity::getSongId, songId);
        }
        if (status != null) {
            wrapper.eq(SongCommentEntity::getStatus, status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SongCommentEntity::getContent, keyword.trim());
        }
        return wrapper.orderByDesc(SongCommentEntity::getCreateTime);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 评论实体
     * @return 评论领域模型
     */
    private SongComment toDomain(SongCommentEntity entity) {
        SongComment comment = new SongComment();
        BeanUtils.copyProperties(entity, comment);
        comment.setStatus(CommentStatus.valueOf(entity.getStatus()));
        return comment;
    }
}

package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.CommentStatus;
import com.bluewhale.video.domain.model.ContentComment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 内容评论仓储端口。
 *
 * @author BlueWhale
 */
public interface CommentRepository {

    /**
     * 新增评论。
     *
     * @param comment 评论
     */
    void insert(ContentComment comment);

    /**
     * 按主键查询评论。
     *
     * @param id 评论 ID
     * @return 评论；不存在返回空
     */
    Optional<ContentComment> findById(Long id);

    /**
     * 分页查询内容的可见顶级评论（按创建时间倒序）。
     *
     * @param contentId 内容 ID
     * @param offset 偏移
     * @param limit 条数
     * @return 评论列表
     */
    List<ContentComment> pageVisibleRoots(Long contentId, long offset, long limit);

    /**
     * 查询多条顶级评论的可见回复。
     *
     * @param parentIds 父评论 ID 集合
     * @return 回复列表（按创建时间升序）
     */
    List<ContentComment> findVisibleReplies(Collection<Long> parentIds);

    /**
     * 统计内容可见评论总数（含回复）。
     *
     * @param contentId 内容 ID
     * @return 总数
     */
    long countVisible(Long contentId);

    /**
     * 查询指定状态的全部评论（审核队列）。
     *
     * @param status 评论状态
     * @param offset 偏移
     * @param limit 条数
     * @return 评论列表（按创建时间升序，先到先审）
     */
    List<ContentComment> pageByStatus(CommentStatus status, long offset, long limit);

    /**
     * 统计指定状态的评论总数。
     *
     * @param status 评论状态
     * @return 总数
     */
    long countByStatus(CommentStatus status);

    /**
     * 更新评论状态。
     *
     * @param id 评论 ID
     * @param status 目标状态
     * @return 影响行数
     */
    int updateStatus(Long id, CommentStatus status);

    /**
     * 逻辑删除评论。
     *
     * @param id 评论 ID
     */
    void delete(Long id);

    /**
     * 原子调整评论点赞数（结果不小于 0）。
     *
     * @param commentId 评论 ID
     * @param delta 增量
     */
    void addLikeCount(Long commentId, long delta);
}

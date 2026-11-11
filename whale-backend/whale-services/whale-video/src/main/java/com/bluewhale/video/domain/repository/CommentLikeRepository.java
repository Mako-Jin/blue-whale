package com.bluewhale.video.domain.repository;

import java.util.Collection;
import java.util.List;

/**
 * 评论点赞仓储端口（唯一键保证幂等）。
 *
 * @author BlueWhale
 */
public interface CommentLikeRepository {

    /**
     * 幂等点赞评论。
     *
     * @param userId 用户 ID
     * @param commentId 评论 ID
     * @return 本次新增返回 {@code true}
     */
    boolean add(Long userId, Long commentId);

    /**
     * 幂等取消点赞。
     *
     * @param userId 用户 ID
     * @param commentId 评论 ID
     * @return 本次真的删掉一行返回 {@code true}
     */
    boolean remove(Long userId, Long commentId);

    /**
     * 批量判断用户是否点赞过评论。
     *
     * @param userId 用户 ID
     * @param commentIds 评论 ID 集合
     * @return 已点赞的评论 ID 列表
     */
    List<Long> findLiked(Long userId, Collection<Long> commentIds);
}

package com.bluewhale.video.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.video.application.command.CommentSubmitCommand;
import com.bluewhale.video.application.view.CommentAuditView;
import com.bluewhale.video.application.view.CommentView;

/**
 * 评论应用服务：发表 / 分页 / 删除 / 点赞 / 审核。
 *
 * @author BlueWhale
 */
public interface CommentAppService {

    /**
     * 用户侧评论分页（仅可见评论，含一级回复）。
     *
     * @param contentId 内容 ID
     * @param userId 当前用户 ID
     * @param current 当前页
     * @param size 每页条数
     * @return 评论分页
     */
    PageResult<CommentView> page(Long contentId, Long userId, long current, long size);

    /**
     * 发表评论（私密 / 不可播内容不允许评论）。
     *
     * @param contentId 内容 ID
     * @param command 评论命令
     * @param userId 当前用户 ID
     * @return 评论视图
     */
    CommentView submit(Long contentId, CommentSubmitCommand command, Long userId);

    /**
     * 删除本人评论（逻辑删除并扣减评论数）。
     *
     * @param commentId 评论 ID
     * @param userId 当前用户 ID
     */
    void delete(Long commentId, Long userId);

    /**
     * 点赞评论（幂等）。
     *
     * @param commentId 评论 ID
     * @param userId 当前用户 ID
     */
    void like(Long commentId, Long userId);

    /**
     * 审核队列分页。
     *
     * @param status 评论状态（默认 PENDING）
     * @param current 当前页
     * @param size 每页条数
     * @return 评论审核分页
     */
    PageResult<CommentAuditView> auditPage(String status, long current, long size);

    /**
     * 审核通过 / 隐藏评论。
     *
     * @param commentId 评论 ID
     * @param approve 通过为 {@code true}，隐藏为 {@code false}
     * @return 评论审核视图
     */
    CommentAuditView audit(Long commentId, boolean approve);
}

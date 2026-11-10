package com.bluewhale.music.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.CommentAuditQueryCommand;
import com.bluewhale.music.application.command.CommentSubmitCommand;
import com.bluewhale.music.application.view.CommentView;

/**
 * 歌曲评论应用服务（用户侧发表 / 分页与运营审核）。
 *
 * @author BlueWhale
 */
public interface CommentAppService {

    /**
     * 发表评论或回复：先发后审模式立即可见，先审后发模式进入待审核。
     *
     * @param songId 歌曲 ID
     * @param command 评论命令
     * @param userId 评论用户 ID
     * @return 评论视图
     */
    CommentView submit(Long songId, CommentSubmitCommand command, Long userId);

    /**
     * 用户侧评论分页：仅返回可见评论，顶级评论倒序并携带可见回复。
     *
     * @param songId 歌曲 ID
     * @param current 页码
     * @param size 每页条数
     * @param viewerId 查看者用户 ID
     * @return 评论分页
     */
    PageResult<CommentView> pageBySong(Long songId, long current, long size, Long viewerId);

    /**
     * 运营审核分页：按状态 / 歌曲 / 关键词过滤。
     *
     * @param command 查询命令
     * @return 评论分页
     */
    PageResult<CommentView> pageAudit(CommentAuditQueryCommand command);

    /**
     * 审核通过：非可见评论变为可见并累加歌曲评论计数。
     *
     * @param id 评论 ID
     * @return 评论视图
     */
    CommentView approve(Long id);

    /**
     * 隐藏评论：立即对外不可见；原为可见评论时扣减歌曲评论计数。
     *
     * @param id 评论 ID
     * @return 评论视图
     */
    CommentView hide(Long id);
}

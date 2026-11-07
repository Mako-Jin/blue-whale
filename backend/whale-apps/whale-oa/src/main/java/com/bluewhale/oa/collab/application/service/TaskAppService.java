package com.bluewhale.oa.collab.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.application.view.CollabViews;
import com.bluewhale.oa.collab.domain.model.CollabQuery;

import java.util.List;

/**
 * 个人任务应用服务。
 *
 * @author BlueWhale
 */
public interface TaskAppService {

    /**
     * 任务分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CollabViews.TaskView> pageTasks(CollabQuery query);

    /**
     * 任务详情。
     *
     * @param id 任务ID
     * @return 任务视图
     */
    CollabViews.TaskView taskDetail(Long id);

    /**
     * 创建任务。
     *
     * @param command 保存命令
     * @return 任务视图
     */
    CollabViews.TaskView createTask(CollabCommands.TaskSaveCommand command);

    /**
     * 编辑任务。
     *
     * @param id 任务ID
     * @param command 保存命令
     * @return 任务视图
     */
    CollabViews.TaskView updateTask(Long id, CollabCommands.TaskSaveCommand command);

    /**
     * 删除任务。
     *
     * @param id 任务ID
     */
    void deleteTask(Long id);

    /**
     * 变更任务状态。
     *
     * @param id 任务ID
     * @param status 目标状态
     * @return 是否有变更
     */
    boolean changeStatus(Long id, String status);

    /**
     * 任务评论列表。
     *
     * @param taskId 任务ID
     * @return 评论列表
     */
    List<CollabViews.CommentView> listComments(Long taskId);

    /**
     * 新增任务评论。
     *
     * @param taskId 任务ID
     * @param command 评论命令
     * @return 评论视图
     */
    CollabViews.CommentView addComment(Long taskId, CollabCommands.CommentSaveCommand command);

    /**
     * 删除任务评论。
     *
     * @param taskId 任务ID
     * @param commentId 评论ID
     */
    void deleteComment(Long taskId, Long commentId);
}

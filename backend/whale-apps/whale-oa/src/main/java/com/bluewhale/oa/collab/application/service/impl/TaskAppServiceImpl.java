package com.bluewhale.oa.collab.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.application.service.TaskAppService;
import com.bluewhale.oa.collab.application.view.CollabViews;
import com.bluewhale.oa.collab.domain.model.CollabQuery;
import com.bluewhale.oa.collab.domain.model.OaTask;
import com.bluewhale.oa.collab.domain.model.TaskComment;
import com.bluewhale.oa.collab.domain.model.TaskPriority;
import com.bluewhale.oa.collab.domain.model.TaskSource;
import com.bluewhale.oa.collab.domain.model.TaskStatus;
import com.bluewhale.oa.collab.domain.repository.CollabRepository;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人任务应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class TaskAppServiceImpl implements TaskAppService {

    private final CollabRepository repository;

    @Override
    public PageResult<CollabViews.TaskView> pageTasks(CollabQuery query) {
        PageResult<OaTask> page = repository.pageTasks(query);
        List<CollabViews.TaskView> records = page.getRecords().stream()
                .map(TaskAppServiceImpl::toTaskView).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public CollabViews.TaskView taskDetail(Long id) {
        return toTaskView(requireTask(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.TaskView createTask(CollabCommands.TaskSaveCommand command) {
        OaTask task = new OaTask();
        task.setTitle(command.getTitle().trim());
        task.setDescription(command.getDescription());
        task.setOwnerId(command.getOwnerId());
        task.setOwnerName(command.getOwnerName());
        task.setCreatorId(command.getCreatorId());
        task.setCreatorName(command.getCreatorName());
        task.setPriority(StringUtils.hasText(command.getPriority())
                ? TaskPriority.of(command.getPriority()).name() : TaskPriority.NORMAL.name());
        task.setStatus(TaskStatus.TODO.name());
        task.setDueDate(command.getDueDate());
        task.setSource(StringUtils.hasText(command.getSource())
                ? TaskSource.of(command.getSource()).name() : TaskSource.PERSONAL.name());
        task.setSourceId(command.getSourceId());
        repository.saveTask(task);
        return toTaskView(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.TaskView updateTask(Long id, CollabCommands.TaskSaveCommand command) {
        OaTask task = requireTask(id);
        if (TaskStatus.of(task.getStatus()) == TaskStatus.DONE
                || TaskStatus.of(task.getStatus()) == TaskStatus.CANCELLED) {
            throw OaErrorCode.exception(OaErrorCode.TASK_STATUS_CONFLICT, "已完成或已取消的任务不可编辑");
        }
        task.setTitle(command.getTitle().trim());
        task.setDescription(command.getDescription());
        task.setOwnerId(command.getOwnerId());
        task.setOwnerName(command.getOwnerName());
        task.setPriority(StringUtils.hasText(command.getPriority())
                ? TaskPriority.of(command.getPriority()).name() : TaskPriority.NORMAL.name());
        task.setDueDate(command.getDueDate());
        if (StringUtils.hasText(command.getSource())) {
            task.setSource(TaskSource.of(command.getSource()).name());
        }
        if (command.getSourceId() != null) {
            task.setSourceId(command.getSourceId());
        }
        repository.updateTask(task);
        return toTaskView(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        requireTask(id);
        repository.deleteTask(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, String status) {
        OaTask task = requireTask(id);
        boolean changed = task.changeStatus(TaskStatus.of(status), LocalDateTime.now());
        if (changed) {
            repository.updateTask(task);
        }
        return changed;
    }

    @Override
    public List<CollabViews.CommentView> listComments(Long taskId) {
        requireTask(taskId);
        return repository.listComments(taskId).stream()
                .map(TaskAppServiceImpl::toCommentView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.CommentView addComment(Long taskId, CollabCommands.CommentSaveCommand command) {
        requireTask(taskId);
        TaskComment comment = new TaskComment();
        comment.setTaskId(taskId);
        comment.setUserId(command.getUserId());
        comment.setUserName(command.getUserName());
        comment.setContent(command.getContent().trim());
        repository.saveComment(comment);
        return toCommentView(comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long taskId, Long commentId) {
        requireTask(taskId);
        TaskComment comment = repository.findComment(commentId);
        if (comment == null || !taskId.equals(comment.getTaskId())) {
            throw OaErrorCode.exception(OaErrorCode.TASK_INVALID, "评论不存在或不属于该任务");
        }
        repository.deleteComment(commentId);
    }

    // ---------------------------------------------------------------- 私有方法

    /**
     * 按标识获取任务，不存在时抛出业务异常。
     *
     * @param id 任务标识
     * @return 任务实体
     */
    private OaTask requireTask(Long id) {
        OaTask task = repository.findTask(id);
        if (task == null) {
            throw OaErrorCode.exception(OaErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /**
     * 将任务实体转换为任务视图。
     *
     * @param task 任务实体
     * @return 任务视图
     */
    private static CollabViews.TaskView toTaskView(OaTask task) {
        CollabViews.TaskView view = new CollabViews.TaskView();
        view.setId(task.getId());
        view.setTitle(task.getTitle());
        view.setDescription(task.getDescription());
        view.setOwnerId(task.getOwnerId());
        view.setOwnerName(task.getOwnerName());
        view.setCreatorId(task.getCreatorId());
        view.setCreatorName(task.getCreatorName());
        view.setPriority(task.getPriority());
        view.setStatus(task.getStatus());
        view.setDueDate(task.getDueDate());
        view.setSource(task.getSource());
        view.setSourceId(task.getSourceId());
        view.setFinishTime(task.getFinishTime());
        view.setCreateTime(task.getCreateTime());
        return view;
    }

    /**
     * 将任务评论实体转换为评论视图。
     *
     * @param comment 任务评论实体
     * @return 评论视图
     */
    private static CollabViews.CommentView toCommentView(TaskComment comment) {
        CollabViews.CommentView view = new CollabViews.CommentView();
        view.setId(comment.getId());
        view.setTaskId(comment.getTaskId());
        view.setUserId(comment.getUserId());
        view.setUserName(comment.getUserName());
        view.setContent(comment.getContent());
        view.setCreateTime(comment.getCreateTime());
        return view;
    }
}

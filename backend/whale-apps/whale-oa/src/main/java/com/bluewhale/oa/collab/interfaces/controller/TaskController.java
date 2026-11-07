package com.bluewhale.oa.collab.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.application.service.TaskAppService;
import com.bluewhale.oa.collab.application.view.CollabViews;
import com.bluewhale.oa.collab.domain.model.CollabQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OA 个人任务接口。
 *
 * <p>服务内路径 {@code /oa/task/**}，受 {@code sys:oa:task:*} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/task")
@RequiredArgsConstructor
public class TaskController {

    private static final String TARGET_TYPE = "oa_task";

    private final TaskAppService taskAppService;

    @GetMapping("/page")
    @RequirePermission("sys:oa:task:list")
    public Result<PageResult<CollabViews.TaskView>> page(
            CollabQuery query, @RequestParam(defaultValue = "false") boolean mine,
            HttpServletRequest request) {
        if (mine) {
            query.setUserId(UserContext.requireUserId(request));
        }
        return Result.ok(taskAppService.pageTasks(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:oa:task:list")
    public Result<CollabViews.TaskView> detail(@PathVariable Long id) {
        return Result.ok(taskAppService.taskDetail(id));
    }

    @PostMapping
    @RequirePermission("sys:oa:task:manage")
    @AuditLog(operation = "创建任务", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<CollabViews.TaskView> create(
            @Valid @RequestBody CollabCommands.TaskSaveCommand command, HttpServletRequest request) {
        command.setCreatorId(UserContext.requireUserId(request));
        command.setCreatorName(WebHeaders.username(request));
        return Result.ok(taskAppService.createTask(command));
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:oa:task:manage")
    @AuditLog(operation = "编辑任务", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CollabViews.TaskView> update(
            @PathVariable Long id, @Valid @RequestBody CollabCommands.TaskSaveCommand command) {
        return Result.ok(taskAppService.updateTask(id, command));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:task:manage")
    @AuditLog(operation = "删除任务", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> delete(@PathVariable Long id) {
        taskAppService.deleteTask(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @RequirePermission("sys:oa:task:manage")
    @AuditLog(operation = "变更任务状态", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<Boolean> changeStatus(
            @PathVariable Long id, @Valid @RequestBody CollabCommands.TaskStatusCommand command) {
        return Result.ok(taskAppService.changeStatus(id, command.getStatus()));
    }

    @GetMapping("/{id}/comments")
    @RequirePermission("sys:oa:task:list")
    public Result<List<CollabViews.CommentView>> comments(@PathVariable Long id) {
        return Result.ok(taskAppService.listComments(id));
    }

    @PostMapping("/{id}/comments")
    @RequirePermission("sys:oa:task:comment")
    @AuditLog(operation = "新增任务评论", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<CollabViews.CommentView> addComment(
            @PathVariable Long id, @Valid @RequestBody CollabCommands.CommentSaveCommand command,
            HttpServletRequest request) {
        command.setUserId(UserContext.requireUserId(request));
        if (!StringUtils.hasText(command.getUserName())) {
            command.setUserName(WebHeaders.username(request));
        }
        return Result.ok(taskAppService.addComment(id, command));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    @RequirePermission("sys:oa:task:comment")
    @AuditLog(operation = "删除任务评论", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> deleteComment(@PathVariable Long id, @PathVariable Long commentId) {
        taskAppService.deleteComment(id, commentId);
        return Result.ok();
    }
}

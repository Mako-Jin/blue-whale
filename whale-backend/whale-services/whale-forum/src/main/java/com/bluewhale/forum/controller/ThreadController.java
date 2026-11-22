package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.ThreadCreateRequest;
import com.bluewhale.forum.dto.ThreadDetailVO;
import com.bluewhale.forum.dto.ThreadListItemVO;
import com.bluewhale.forum.dto.ThreadQuery;
import com.bluewhale.forum.dto.ThreadUpdateRequest;
import com.bluewhale.forum.service.ThreadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子接口：发帖、列表、详情、编辑、删除。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/forum/v{version}/thread", version = "1")
@RequiredArgsConstructor
public class ThreadController {

    private final ThreadService threadService;

    /**
     * 发帖。
     *
     * @param body 发帖请求
     * @param request 当前请求
     * @return 新帖详情（审核版块返回 PENDING）
     */
    @PostMapping
    @RequirePermission(ForumPermissions.THREAD_ADD)
    @AuditLog(operation = "发布论坛帖子", type = OperateType.CREATE, targetType = "fm_thread")
    public Result<ThreadDetailVO> create(@Valid @RequestBody ThreadCreateRequest body,
                                         HttpServletRequest request) {
        return Result.ok(threadService.create(body, ForumActor.from(request)));
    }

    /**
     * 帖子列表（按版块分页）。
     *
     * @param query 查询条件
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<PageResult<ThreadListItemVO>> page(ThreadQuery query, HttpServletRequest request) {
        return Result.ok(threadService.page(query, ForumActor.from(request)));
    }

    /**
     * 帖子详情。
     *
     * @param id 帖子ID
     * @param request 当前请求
     * @return 帖子详情
     */
    @GetMapping("/{id}")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<ThreadDetailVO> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(threadService.detail(id, ForumActor.from(request)));
    }

    /**
     * 编辑帖子（审核版块内自动退回待审）。
     *
     * @param id 帖子ID
     * @param body 编辑请求
     * @param request 当前请求
     * @return 编辑后的详情
     */
    @PutMapping("/{id}")
    @RequirePermission(ForumPermissions.THREAD_EDIT)
    @AuditLog(operation = "编辑论坛帖子", type = OperateType.UPDATE, targetType = "fm_thread")
    public Result<ThreadDetailVO> update(@PathVariable("id") Long id,
                                         @RequestBody ThreadUpdateRequest body,
                                         HttpServletRequest request) {
        return Result.ok(threadService.update(id, body, ForumActor.from(request)));
    }

    /**
     * 删除帖子（软删）。
     *
     * @param id 帖子ID
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission(ForumPermissions.THREAD_REMOVE)
    @AuditLog(operation = "删除论坛帖子", type = OperateType.DELETE, targetType = "fm_thread")
    public Result<Void> delete(@PathVariable("id") Long id, HttpServletRequest request) {
        threadService.delete(id, ForumActor.from(request));
        return Result.ok();
    }
}

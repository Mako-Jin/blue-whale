package com.bluewhale.oa.collab.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.application.service.NoticeAppService;
import com.bluewhale.oa.collab.application.view.CollabViews;
import com.bluewhale.oa.collab.domain.model.CollabQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * OA 公告接口。
 *
 * <p>服务内路径 {@code /oa/notice/**}；发布/撤回/已读状态均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/notice")
@RequiredArgsConstructor
public class NoticeController {

    private static final String TARGET_TYPE = "oa_notice";

    private final NoticeAppService noticeAppService;

    @GetMapping("/page")
    @RequirePermission("sys:oa:notice:list")
    public Result<PageResult<CollabViews.NoticeView>> page(CollabQuery query) {
        return Result.ok(noticeAppService.pageNotices(query));
    }

    @GetMapping("/my/page")
    @RequirePermission("sys:oa:notice:list")
    public Result<PageResult<CollabViews.NoticeView>> myPage(CollabQuery query, HttpServletRequest request) {
        query.setUserId(UserContext.requireUserId(request));
        return Result.ok(noticeAppService.pageMyNotices(query));
    }

    @GetMapping("/recipients")
    @RequirePermission("sys:oa:notice:manage")
    public Result<List<CollabViews.CandidateView>> recipients(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(noticeAppService.recipientCandidates(keyword, deptId, limit));
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:oa:notice:list")
    public Result<CollabViews.NoticeDetailView> detail(@PathVariable Long id) {
        return Result.ok(noticeAppService.noticeDetail(id));
    }

    @PostMapping
    @RequirePermission("sys:oa:notice:manage")
    @AuditLog(operation = "新建公告", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<CollabViews.NoticeView> create(@Valid @RequestBody CollabCommands.NoticeSaveCommand command) {
        return Result.ok(noticeAppService.createNotice(command));
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:oa:notice:manage")
    @AuditLog(operation = "编辑公告", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CollabViews.NoticeView> update(
            @PathVariable Long id, @Valid @RequestBody CollabCommands.NoticeSaveCommand command) {
        return Result.ok(noticeAppService.updateNotice(id, command));
    }

    @PutMapping("/{id}/publish")
    @RequirePermission("sys:oa:notice:manage")
    @AuditLog(operation = "发布公告", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CollabViews.NoticeView> publish(@PathVariable Long id, HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        return Result.ok(noticeAppService.publishNotice(id, userId, WebHeaders.username(request)));
    }

    @PutMapping("/{id}/withdraw")
    @RequirePermission("sys:oa:notice:manage")
    @AuditLog(operation = "撤回公告", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CollabViews.NoticeView> withdraw(@PathVariable Long id) {
        return Result.ok(noticeAppService.withdrawNotice(id));
    }

    @PutMapping("/{id}/read")
    @RequirePermission("sys:oa:notice:list")
    @AuditLog(operation = "标记公告已读", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<Void> read(@PathVariable Long id, HttpServletRequest request) {
        noticeAppService.markRead(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    @GetMapping("/{id}/stats")
    @RequirePermission("sys:oa:notice:stats")
    public Result<CollabViews.NoticeDetailView> stats(@PathVariable Long id) {
        return Result.ok(noticeAppService.readStats(id));
    }
}

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

    /**
     * 分页查询公告。
     *
     * @param query 查询条件
     * @return 公告分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:notice:list")
    public Result<PageResult<CollabViews.NoticeView>> page(CollabQuery query) {
        return Result.ok(noticeAppService.pageNotices(query));
    }

    /**
     * 分页查询当前登录用户的公告。
     *
     * @param query 查询条件
     * @param request 当前请求，用于解析登录用户
     * @return 公告分页结果
     */
    @GetMapping("/my/page")
    @RequirePermission("sys:oa:notice:list")
    public Result<PageResult<CollabViews.NoticeView>> myPage(CollabQuery query, HttpServletRequest request) {
        query.setUserId(UserContext.requireUserId(request));
        return Result.ok(noticeAppService.pageMyNotices(query));
    }

    /**
     * 查询公告接收人候选，供发布时选择接收人。
     *
     * @param keyword 用户名或昵称关键字
     * @param deptId 部门ID
     * @param limit 返回条数上限
     * @return 接收人候选列表
     */
    @GetMapping("/recipients")
    @RequirePermission("sys:oa:notice:manage")
    public Result<List<CollabViews.CandidateView>> recipients(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(noticeAppService.recipientCandidates(keyword, deptId, limit));
    }

    /**
     * 查询公告详情。
     *
     * @param id 公告ID
     * @return 公告详情视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:notice:list")
    public Result<CollabViews.NoticeDetailView> detail(@PathVariable Long id) {
        return Result.ok(noticeAppService.noticeDetail(id));
    }

    /**
     * 新建公告（草稿）。
     *
     * @param command 公告保存命令
     * @return 新建的公告视图
     */
    @PostMapping
    @RequirePermission("sys:oa:notice:manage")
    @AuditLog(operation = "新建公告", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<CollabViews.NoticeView> create(@Valid @RequestBody CollabCommands.NoticeSaveCommand command) {
        return Result.ok(noticeAppService.createNotice(command));
    }

    /**
     * 编辑公告。
     *
     * @param id 公告ID
     * @param command 公告保存命令
     * @return 更新后的公告视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:notice:manage")
    @AuditLog(operation = "编辑公告", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CollabViews.NoticeView> update(
            @PathVariable Long id, @Valid @RequestBody CollabCommands.NoticeSaveCommand command) {
        return Result.ok(noticeAppService.updateNotice(id, command));
    }

    /**
     * 发布公告，以当前登录用户作为发布人。
     *
     * @param id 公告ID
     * @param request 当前请求，用于解析登录用户
     * @return 发布后的公告视图
     */
    @PutMapping("/{id}/publish")
    @RequirePermission("sys:oa:notice:manage")
    @AuditLog(operation = "发布公告", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CollabViews.NoticeView> publish(@PathVariable Long id, HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        return Result.ok(noticeAppService.publishNotice(id, userId, WebHeaders.username(request)));
    }

    /**
     * 撤回公告。
     *
     * @param id 公告ID
     * @return 撤回后的公告视图
     */
    @PutMapping("/{id}/withdraw")
    @RequirePermission("sys:oa:notice:manage")
    @AuditLog(operation = "撤回公告", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CollabViews.NoticeView> withdraw(@PathVariable Long id) {
        return Result.ok(noticeAppService.withdrawNotice(id));
    }

    /**
     * 将公告标记为当前登录用户已读。
     *
     * @param id 公告ID
     * @param request 当前请求，用于解析登录用户
     * @return 空结果
     */
    @PutMapping("/{id}/read")
    @RequirePermission("sys:oa:notice:list")
    @AuditLog(operation = "标记公告已读", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<Void> read(@PathVariable Long id, HttpServletRequest request) {
        noticeAppService.markRead(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 查询公告阅读统计，详情中携带接收人与已读数据。
     *
     * @param id 公告ID
     * @return 含阅读统计的公告详情视图
     */
    @GetMapping("/{id}/stats")
    @RequirePermission("sys:oa:notice:stats")
    public Result<CollabViews.NoticeDetailView> stats(@PathVariable Long id) {
        return Result.ok(noticeAppService.readStats(id));
    }
}

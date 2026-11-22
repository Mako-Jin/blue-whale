package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.service.CommentAppService;
import com.bluewhale.video.application.view.CommentAuditView;
import com.bluewhale.video.interfaces.support.PageParams;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论审核运营接口。
 *
 * <p>要求 {@code sys:video:comment:audit}；通过与隐藏操作幂等并留审计，
 * 隐藏后评论立即对用户不可见。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/video/v{version}/admin/comment", version = "1")
@RequiredArgsConstructor
public class VideoCommentAuditController {

    private final CommentAppService commentAppService;

    /**
     * 审核队列分页。
     *
     * @param status 状态过滤：PENDING / VISIBLE / HIDDEN，可选
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @return 评论审核分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:video:comment:audit")
    public Result<PageResult<CommentAuditView>> page(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        return Result.ok(commentAppService.auditPage(status, PageParams.current(current, pageNum),
                PageParams.size(size, pageSize)));
    }

    /**
     * 审核通过（PENDING / HIDDEN → VISIBLE，幂等）。
     *
     * @param id 评论 ID
     * @return 评论审核视图
     */
    @PostMapping("/{id}/approve")
    @RequirePermission("sys:video:comment:audit")
    @AuditLog(operation = "评论审核通过", type = OperateType.UPDATE, targetType = "vd_comment", targetId = "#id")
    public Result<CommentAuditView> approve(@PathVariable Long id) {
        return Result.ok(commentAppService.audit(id, true));
    }

    /**
     * 隐藏评论（→ HIDDEN，幂等，立即对用户不可见）。
     *
     * @param id 评论 ID
     * @return 评论审核视图
     */
    @PostMapping("/{id}/hide")
    @RequirePermission("sys:video:comment:audit")
    @AuditLog(operation = "评论隐藏", type = OperateType.UPDATE, targetType = "vd_comment", targetId = "#id")
    public Result<CommentAuditView> hide(@PathVariable Long id) {
        return Result.ok(commentAppService.audit(id, false));
    }
}

package com.bluewhale.music.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.music.application.command.CommentAuditQueryCommand;
import com.bluewhale.music.application.service.CommentAppService;
import com.bluewhale.music.application.view.CommentView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论审核运营接口（M4）。
 *
 * <p>要求 {@code sys:music:comment:audit}；通过与隐藏操作幂等并留审计，
 * 隐藏后评论立即对用户不可见。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/music/comment")
@RequiredArgsConstructor
public class CommentAuditController {

    private final CommentAppService commentAppService;

    /**
     * 审核队列分页。
     *
     * @param songId 歌曲 ID，可选
     * @param status 状态过滤：PENDING / VISIBLE / HIDDEN，可选
     * @param keyword 正文关键词，可选
     * @param current 页码
     * @param size 每页条数
     * @return 评论分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:music:comment:audit")
    public Result<PageResult<CommentView>> page(
            @RequestParam(value = "songId", required = false) Long songId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        CommentAuditQueryCommand command = new CommentAuditQueryCommand();
        command.setSongId(songId);
        command.setStatus(status);
        command.setKeyword(keyword);
        command.setCurrent(current);
        command.setSize(size);
        return Result.ok(commentAppService.pageAudit(command));
    }

    /**
     * 审核通过（PENDING / HIDDEN → VISIBLE，幂等）。
     *
     * @param id 评论 ID
     * @return 评论视图
     */
    @PostMapping("/{id}/approve")
    @RequirePermission("sys:music:comment:audit")
    @AuditLog(operation = "评论审核通过", type = OperateType.UPDATE, targetType = "mu_song_comment", targetId = "#id")
    public Result<CommentView> approve(@PathVariable Long id) {
        return Result.ok(commentAppService.approve(id));
    }

    /**
     * 隐藏评论（→ HIDDEN，幂等，立即对用户不可见）。
     *
     * @param id 评论 ID
     * @return 评论视图
     */
    @PostMapping("/{id}/hide")
    @RequirePermission("sys:music:comment:audit")
    @AuditLog(operation = "评论隐藏", type = OperateType.UPDATE, targetType = "mu_song_comment", targetId = "#id")
    public Result<CommentView> hide(@PathVariable Long id) {
        return Result.ok(commentAppService.hide(id));
    }
}

package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.command.CommentSubmitCommand;
import com.bluewhale.video.application.service.CommentAppService;
import com.bluewhale.video.application.view.CommentView;
import com.bluewhale.video.interfaces.support.PageParams;
import com.bluewhale.video.interfaces.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容评论用户侧接口：发表 / 分页 / 删除 / 点赞。
 *
 * <p>发表与点赞要求 {@code sys:video:comment:create}，查看要求 {@code sys:video:browse}；
 * 仅可见评论对外展示，删除仅限本人。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoCommentController {

    private final CommentAppService commentAppService;

    /**
     * 发表评论或回复。
     *
     * @param id 内容 ID
     * @param command 评论命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 评论视图
     */
    @PostMapping("/content/{id}/comment")
    @RequirePermission("sys:video:comment:create")
    public Result<CommentView> submit(@PathVariable Long id,
                                      @Valid @RequestBody CommentSubmitCommand command,
                                      HttpServletRequest request) {
        return Result.ok(commentAppService.submit(id, command, UserContext.requireUserId(request)));
    }

    /**
     * 内容评论分页（仅可见评论，顶级评论携带一级回复）。
     *
     * @param id 内容 ID
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 评论分页
     */
    @GetMapping("/content/{id}/comment/page")
    @RequirePermission("sys:video:browse")
    public Result<PageResult<CommentView>> page(
            @PathVariable Long id,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(commentAppService.page(id, UserContext.optionalUserId(request),
                PageParams.current(current, pageNum), PageParams.size(size, pageSize)));
    }

    /**
     * 删除本人评论（逻辑删除并扣减评论数）。
     *
     * @param id 评论 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @DeleteMapping("/comment/{id}")
    @RequirePermission("sys:video:comment:create")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        commentAppService.delete(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 点赞评论（幂等）。
     *
     * @param id 评论 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PostMapping("/comment/{id}/like")
    @RequirePermission("sys:video:comment:create")
    public Result<Void> like(@PathVariable Long id, HttpServletRequest request) {
        commentAppService.like(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 取消点赞评论（幂等）。
     *
     * <p>与 {@link #like} 成对：前端 {@code likeComment(id, false)} 走的就是 DELETE，
     * 此前只实现了 POST，取消点赞会 405，前端只能看着按钮状态跳变但服务端不变。</p>
     *
     * @param id 评论 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @DeleteMapping("/comment/{id}/like")
    @RequirePermission("sys:video:comment:create")
    public Result<Void> unlike(@PathVariable Long id, HttpServletRequest request) {
        commentAppService.unlike(id, UserContext.requireUserId(request));
        return Result.ok();
    }
}

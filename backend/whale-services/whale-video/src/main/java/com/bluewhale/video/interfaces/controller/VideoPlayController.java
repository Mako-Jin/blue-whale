package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.command.PlayProgressCommand;
import com.bluewhale.video.application.command.PlayTicketBatchCommand;
import com.bluewhale.video.application.service.PlayAppService;
import com.bluewhale.video.application.view.PlayTicketBatchView;
import com.bluewhale.video.application.view.PlayTicketView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.application.view.WatchHistoryView;
import com.bluewhale.video.interfaces.support.PageParams;
import com.bluewhale.video.interfaces.support.UserContext;
import com.bluewhale.video.interfaces.vo.PlayTicketRequest;
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
 * 播放与互动接口：播放票据、进度、最近观看、点赞与收藏。
 *
 * <p>票据绑定当前租户 / 用户 / 内容；点赞与收藏幂等，重复请求不重复计数。
 * 最近观看支持删除单条记录。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoPlayController {

    private final PlayAppService playAppService;

    /**
     * 申请单个内容的播放票据。
     *
     * @param requestBody 票据请求
     * @param request 当前请求（读取网关注入的用户头）
     * @return 票据视图
     */
    @PostMapping("/play/ticket")
    @RequirePermission("sys:video:browse")
    public Result<PlayTicketView> ticket(@Valid @RequestBody PlayTicketRequest requestBody,
                                         HttpServletRequest request) {
        return Result.ok(playAppService.issueTicket(requestBody.getContentId(),
                UserContext.requireUserId(request), TenantContext.requireId()));
    }

    /**
     * 批量签发播放票据（feed / 列表预取）。
     *
     * @param command 批量票据命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 批量票据视图
     */
    @PostMapping("/play/tickets")
    @RequirePermission("sys:video:browse")
    public Result<PlayTicketBatchView> tickets(@Valid @RequestBody PlayTicketBatchCommand command,
                                               HttpServletRequest request) {
        return Result.ok(playAppService.issueTickets(command.getContentIds(),
                UserContext.requireUserId(request), TenantContext.requireId()));
    }

    /**
     * 上报观看进度（同一内容重复上报幂等）。
     *
     * @param command 进度命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PostMapping("/play/progress")
    @RequirePermission("sys:video:browse")
    public Result<Void> progress(@Valid @RequestBody PlayProgressCommand command, HttpServletRequest request) {
        playAppService.reportProgress(command, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 最近观看分页。
     *
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 历史分页
     */
    @GetMapping("/history")
    @RequirePermission("sys:video:browse")
    public Result<PageResult<WatchHistoryView>> history(
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(playAppService.recentHistory(UserContext.requireUserId(request),
                PageParams.current(current, pageNum), PageParams.size(size, pageSize)));
    }

    /**
     * 删除本人对指定内容的观看记录（幂等）。
     *
     * @param contentId 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @DeleteMapping("/history/{contentId}")
    @RequirePermission("sys:video:browse")
    public Result<Void> deleteHistory(@PathVariable Long contentId, HttpServletRequest request) {
        playAppService.removeHistory(contentId, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 点赞内容（幂等）。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PostMapping("/content/{id}/like")
    @RequirePermission("sys:video:like")
    public Result<Void> like(@PathVariable Long id, HttpServletRequest request) {
        playAppService.like(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 取消点赞（幂等）。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @DeleteMapping("/content/{id}/like")
    @RequirePermission("sys:video:like")
    public Result<Void> unlike(@PathVariable Long id, HttpServletRequest request) {
        playAppService.unlike(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 收藏内容（幂等）。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PostMapping("/content/{id}/favorite")
    @RequirePermission("sys:video:favorite")
    public Result<Void> favorite(@PathVariable Long id, HttpServletRequest request) {
        playAppService.favorite(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 取消收藏（幂等）。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @DeleteMapping("/content/{id}/favorite")
    @RequirePermission("sys:video:favorite")
    public Result<Void> unfavorite(@PathVariable Long id, HttpServletRequest request) {
        playAppService.unfavorite(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 我的点赞分页。
     *
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 用户侧分页
     */
    @GetMapping("/my/likes")
    @RequirePermission("sys:video:like")
    public Result<PageResult<PublicContentView>> myLikes(
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(playAppService.myLikes(UserContext.requireUserId(request),
                PageParams.current(current, pageNum), PageParams.size(size, pageSize)));
    }

    /**
     * 我的收藏分页。
     *
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 用户侧分页
     */
    @GetMapping("/my/favorites")
    @RequirePermission("sys:video:favorite")
    public Result<PageResult<PublicContentView>> myFavorites(
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(playAppService.myFavorites(UserContext.requireUserId(request),
                PageParams.current(current, pageNum), PageParams.size(size, pageSize)));
    }
}

package com.bluewhale.music.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.music.application.command.PlayProgressCommand;
import com.bluewhale.music.application.command.PlayTicketCommand;
import com.bluewhale.music.application.service.PlayAppService;
import com.bluewhale.music.application.view.PlayHistoryView;
import com.bluewhale.music.application.view.PlayTicketView;
import com.bluewhale.music.application.view.PublicSongView;
import com.bluewhale.music.interfaces.support.UserContext;
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
 * 播放票据、播放进度、最近播放与收藏接口（M2）。
 *
 * <p>票据申请要求 {@code sys:music:browse} 且绑定当前租户 / 用户 / 歌曲；
 * 收藏要求 {@code sys:music:like}。流式播放走独立的 {@code /music/stream/{songId}}
 * 票据代理，不在此控制器内暴露任何文件 ID。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/music/v{version}", version = "1")
@RequiredArgsConstructor
public class PlayController {

    private final PlayAppService playAppService;

    /**
     * 申请播放票据。
     *
     * @param command 票据命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 票据与播放元数据
     */
    @PostMapping("/play/ticket")
    @RequirePermission("sys:music:browse")
    public Result<PlayTicketView> ticket(@Valid @RequestBody PlayTicketCommand command,
                                         HttpServletRequest request) {
        return Result.ok(playAppService.issueTicket(command.getSongId(),
                UserContext.requireUserId(request), TenantContext.requireId()));
    }

    /**
     * 上报播放进度（同一歌曲重复上报幂等）。
     *
     * @param command 播放进度命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PostMapping("/play/progress")
    @RequirePermission("sys:music:browse")
    public Result<Void> progress(@Valid @RequestBody PlayProgressCommand command,
                                 HttpServletRequest request) {
        playAppService.reportProgress(command, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 最近播放分页。
     *
     * @param current 页码
     * @param size 每页条数
     * @param request 当前请求（读取网关注入的用户头）
     * @return 播放历史分页
     */
    @GetMapping("/play/history")
    @RequirePermission("sys:music:browse")
    public Result<PageResult<PlayHistoryView>> history(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        return Result.ok(playAppService.recentHistory(UserContext.requireUserId(request), current, size));
    }

    /**
     * 收藏歌曲（幂等）。
     *
     * @param id 歌曲 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PostMapping("/song/{id}/like")
    @RequirePermission("sys:music:like")
    public Result<Void> like(@PathVariable Long id, HttpServletRequest request) {
        playAppService.like(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 取消收藏（幂等）。
     *
     * @param id 歌曲 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @DeleteMapping("/song/{id}/like")
    @RequirePermission("sys:music:like")
    public Result<Void> unlike(@PathVariable Long id, HttpServletRequest request) {
        playAppService.unlike(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 我的收藏分页。
     *
     * @param current 页码
     * @param size 每页条数
     * @param request 当前请求（读取网关注入的用户头）
     * @return 用户侧歌曲分页
     */
    @GetMapping("/my/likes")
    @RequirePermission("sys:music:like")
    public Result<PageResult<PublicSongView>> myLikes(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        return Result.ok(playAppService.myLikes(UserContext.requireUserId(request), current, size));
    }
}

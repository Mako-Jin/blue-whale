package com.bluewhale.music.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.music.application.command.CommentSubmitCommand;
import com.bluewhale.music.application.service.CommentAppService;
import com.bluewhale.music.application.view.CommentView;
import com.bluewhale.music.interfaces.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 歌曲评论用户侧接口（M4）。
 *
 * <p>发表评论要求 {@code sys:music:comment:create}，查看评论要求 {@code sys:music:browse}。
 * 评论正文为纯文本，仅可见评论对外展示。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/music/v{version}", version = "1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentAppService commentAppService;

    /**
     * 发表评论或回复。
     *
     * @param id 歌曲 ID
     * @param command 评论命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 评论视图
     */
    @PostMapping("/song/{id}/comment")
    @RequirePermission("sys:music:comment:create")
    public Result<CommentView> submit(@PathVariable Long id,
                                      @Valid @RequestBody CommentSubmitCommand command,
                                      HttpServletRequest request) {
        return Result.ok(commentAppService.submit(id, command, UserContext.requireUserId(request)));
    }

    /**
     * 歌曲评论分页（仅可见评论，顶级评论携带回复）。
     *
     * @param id 歌曲 ID
     * @param current 页码
     * @param size 每页条数
     * @param request 当前请求（读取网关注入的用户头）
     * @return 评论分页
     */
    @GetMapping("/song/{id}/comment/page")
    @RequirePermission("sys:music:browse")
    public Result<PageResult<CommentView>> page(
            @PathVariable Long id,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        return Result.ok(commentAppService.pageBySong(id, current, size,
                UserContext.requireUserId(request)));
    }
}

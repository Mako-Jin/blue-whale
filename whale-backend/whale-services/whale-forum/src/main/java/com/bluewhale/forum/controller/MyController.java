package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.MyPostVO;
import com.bluewhale.forum.dto.ThreadListItemVO;
import com.bluewhale.forum.service.PostService;
import com.bluewhale.forum.service.ThreadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的内容接口（F-07-02）。
 *
 * <p>一律以身份头用户为准，不接受 {@code userId} 入参——「我的」允许传 userId
 * 等于开了一个越权读入口。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/forum/my")
@RequiredArgsConstructor
public class MyController {

    private final ThreadService threadService;

    private final PostService postService;

    /**
     * 我的帖子（含审核中 / 已处置）。
     *
     * @param current 页码
     * @param size 每页条数
     * @param request 当前请求
     * @return 帖子分页
     */
    @GetMapping("/threads")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<PageResult<ThreadListItemVO>> threads(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "20") Long size,
            HttpServletRequest request) {
        return Result.ok(threadService.myThreads(current, size, ForumActor.from(request)));
    }

    /**
     * 我的回复（排除所在帖子已删的楼层）。
     *
     * @param current 页码
     * @param size 每页条数
     * @param request 当前请求
     * @return 回复分页
     */
    @GetMapping("/posts")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<PageResult<MyPostVO>> posts(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "20") Long size,
            HttpServletRequest request) {
        return Result.ok(postService.myPosts(current, size, ForumActor.from(request)));
    }
}

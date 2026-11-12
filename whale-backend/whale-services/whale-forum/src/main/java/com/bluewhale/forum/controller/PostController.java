package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.PostCreateRequest;
import com.bluewhale.forum.dto.PostVO;
import com.bluewhale.forum.service.PostService;
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
 * 楼层接口：回帖与楼层分页。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/forum/thread/{threadId}/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * 回帖（楼层号由服务端分配）。
     *
     * @param threadId 帖子ID
     * @param body 回帖请求
     * @param request 当前请求
     * @return 新楼层
     */
    @PostMapping
    @RequirePermission(ForumPermissions.POST_ADD)
    @AuditLog(operation = "论坛回帖", type = OperateType.CREATE, targetType = "fm_post")
    public Result<PostVO> create(@PathVariable("threadId") Long threadId,
                                 @Valid @RequestBody PostCreateRequest body,
                                 HttpServletRequest request) {
        return Result.ok(postService.create(threadId, body, ForumActor.from(request)));
    }

    /**
     * 楼层分页（默认按楼层号升序）。
     *
     * @param threadId 帖子ID
     * @param current 页码
     * @param size 每页条数
     * @param order 排序：asc 正序（默认）/ desc 倒序（热帖从最新楼层看起）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<PageResult<PostVO>> page(@PathVariable("threadId") Long threadId,
                                           @RequestParam(value = "current", defaultValue = "1") Long current,
                                           @RequestParam(value = "size", defaultValue = "20") Long size,
                                           @RequestParam(value = "order", defaultValue = "asc") String order,
                                           HttpServletRequest request) {
        return Result.ok(postService.page(threadId, current, size, order, ForumActor.from(request)));
    }
}

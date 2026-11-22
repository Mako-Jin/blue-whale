package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.ReactionStateVO;
import com.bluewhale.forum.dto.ThreadListItemVO;
import com.bluewhale.forum.service.ReactionService;
import com.bluewhale.forum.service.ThreadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 互动接口：投花（LIKE）、藏帖（COLLECT）、我的收藏。
 *
 * <p>投花用 PUT / 取消用 DELETE（同一个 URL），语义是「把状态置为目标值」，
 * 因此重复调用 PUT 不会 400、也不会把计数加两次。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/forum/v{version}", version = "1")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    private final ThreadService threadService;

    /**
     * 投花 / 收藏。
     *
     * @param id 帖子ID
     * @param type 互动类型：LIKE（默认）/ COLLECT
     * @param request 当前请求
     * @return 操作后的选中态与计数
     */
    @PutMapping("/reaction/thread/{id}")
    @RequirePermission(ForumPermissions.REACTION_USE)
    @AuditLog(operation = "论坛投花/收藏", type = OperateType.UPDATE, targetType = "fm_reaction")
    public Result<ReactionStateVO> add(@PathVariable("id") Long id,
                                       @RequestParam(value = "type", defaultValue = "LIKE") String type,
                                       HttpServletRequest request) {
        return Result.ok(reactionService.toggle(id, type, true, ForumActor.from(request)));
    }

    /**
     * 取消投花 / 取消收藏。
     *
     * @param id 帖子ID
     * @param type 互动类型：LIKE（默认）/ COLLECT
     * @param request 当前请求
     * @return 操作后的选中态与计数
     */
    @DeleteMapping("/reaction/thread/{id}")
    @RequirePermission(ForumPermissions.REACTION_USE)
    @AuditLog(operation = "论坛取消投花/收藏", type = OperateType.UPDATE, targetType = "fm_reaction")
    public Result<ReactionStateVO> cancel(@PathVariable("id") Long id,
                                          @RequestParam(value = "type", defaultValue = "LIKE") String type,
                                          HttpServletRequest request) {
        return Result.ok(reactionService.toggle(id, type, false, ForumActor.from(request)));
    }

    /**
     * 当前用户对某帖的互动状态（详情页按钮选中态）。
     *
     * @param id 帖子ID
     * @param request 当前请求
     * @return 互动状态
     */
    @GetMapping("/thread/{id}/reactions/my")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<ReactionStateVO> my(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(reactionService.state(id, ForumActor.from(request)));
    }

    /**
     * 我的收藏（按收藏时间倒序）。
     *
     * @param current 页码
     * @param size 条数
     * @param request 当前请求
     * @return 帖子分页
     */
    @GetMapping("/my/collections")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<PageResult<ThreadListItemVO>> collections(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        return Result.ok(threadService.collections(current, size, ForumActor.from(request)));
    }
}

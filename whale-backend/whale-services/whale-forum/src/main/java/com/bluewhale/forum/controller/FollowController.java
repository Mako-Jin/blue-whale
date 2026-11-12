package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.FollowStateVO;
import com.bluewhale.forum.dto.FollowVO;
import com.bluewhale.forum.service.FollowService;
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
 * 结契接口（F-05-03：关注作者 / 版块）。
 *
 * <p>关注用 PUT、取关用 DELETE（同一 URL），语义是「把状态置为目标值」：
 * 重复调用不报错，与投花 / 藏帖保持一致。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/forum")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /**
     * 关注作者 / 版块。
     *
     * @param targetType 对象类型：USER / BOARD
     * @param targetId 对象ID
     * @param request 当前请求
     * @return 关注态
     */
    @PutMapping("/follow/{targetType}/{targetId}")
    @RequirePermission(ForumPermissions.REACTION_USE)
    @AuditLog(operation = "论坛关注", type = OperateType.UPDATE, targetType = "fm_follow")
    public Result<FollowStateVO> follow(@PathVariable("targetType") String targetType,
                                        @PathVariable("targetId") Long targetId,
                                        HttpServletRequest request) {
        return Result.ok(followService.follow(targetType, targetId, ForumActor.from(request)));
    }

    /**
     * 取关。
     *
     * @param targetType 对象类型：USER / BOARD
     * @param targetId 对象ID
     * @param request 当前请求
     * @return 关注态
     */
    @DeleteMapping("/follow/{targetType}/{targetId}")
    @RequirePermission(ForumPermissions.REACTION_USE)
    @AuditLog(operation = "论坛取关", type = OperateType.UPDATE, targetType = "fm_follow")
    public Result<FollowStateVO> unfollow(@PathVariable("targetType") String targetType,
                                          @PathVariable("targetId") Long targetId,
                                          HttpServletRequest request) {
        return Result.ok(followService.unfollow(targetType, targetId, ForumActor.from(request)));
    }

    /**
     * 当前用户对某对象的关注态（详情页 / 版块页按钮选中态）。
     *
     * @param targetType 对象类型：USER / BOARD
     * @param targetId 对象ID
     * @param request 当前请求
     * @return 关注态
     */
    @GetMapping("/follow/{targetType}/{targetId}")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<FollowStateVO> state(@PathVariable("targetType") String targetType,
                                       @PathVariable("targetId") Long targetId,
                                       HttpServletRequest request) {
        return Result.ok(followService.state(targetType, targetId, ForumActor.from(request)));
    }

    /**
     * 我的关注（按关注时间倒序）。
     *
     * @param targetType 可选过滤：USER / BOARD
     * @param current 页码
     * @param size 每页条数
     * @param request 当前请求
     * @return 关注分页
     */
    @GetMapping("/my/follows")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<PageResult<FollowVO>> myFollows(
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        return Result.ok(followService.myFollows(targetType, current, size, ForumActor.from(request)));
    }
}

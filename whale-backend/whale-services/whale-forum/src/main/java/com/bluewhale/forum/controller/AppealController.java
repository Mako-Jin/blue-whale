package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.AppealCreateRequest;
import com.bluewhale.forum.dto.AppealHandleRequest;
import com.bluewhale.forum.dto.AppealVO;
import com.bluewhale.forum.service.AppealService;
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
 * 申诉接口（F-09-08）。
 *
 * <p>提交与「我的申诉」用浏览权限：被禁言用户仍可浏览，申诉入口不能因禁言消失。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/forum")
@RequiredArgsConstructor
public class AppealController {

    private final AppealService appealService;

    /**
     * 提交申诉。
     *
     * @param body 申诉内容
     * @param request 当前请求
     * @return 工单
     */
    @PostMapping("/appeal")
    @RequirePermission(ForumPermissions.BROWSE)
    @AuditLog(operation = "论坛提交申诉", type = OperateType.CREATE, targetType = "fm_appeal")
    public Result<AppealVO> submit(@Valid @RequestBody AppealCreateRequest body,
                                   HttpServletRequest request) {
        return Result.ok(appealService.submit(body, ForumActor.from(request)));
    }

    /**
     * 我的申诉。
     *
     * @param current 页码
     * @param size 每页条数
     * @param request 当前请求
     * @return 分页
     */
    @GetMapping("/my/appeals")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<PageResult<AppealVO>> myAppeals(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size,
            HttpServletRequest request) {
        return Result.ok(appealService.myAppeals(current, size, ForumActor.from(request)));
    }

    /**
     * 管理端申诉工单列表。
     *
     * @param status 可选状态过滤
     * @param current 页码
     * @param size 每页条数
     * @return 分页
     */
    @GetMapping("/admin/appeal/page")
    @RequirePermission(ForumPermissions.MANAGE_DISPOSE)
    public Result<PageResult<AppealVO>> page(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        return Result.ok(appealService.page(status, current, size));
    }

    /**
     * 复核申诉。
     *
     * @param id 工单ID
     * @param body 复核结论
     * @param request 当前请求
     * @return 更新后的工单
     */
    @PostMapping("/admin/appeal/{id}/handle")
    @RequirePermission(ForumPermissions.MANAGE_DISPOSE)
    @AuditLog(operation = "论坛申诉复核", type = OperateType.UPDATE, targetType = "fm_appeal")
    public Result<AppealVO> handle(@PathVariable("id") Long id,
                                   @Valid @RequestBody AppealHandleRequest body,
                                   HttpServletRequest request) {
        return Result.ok(appealService.handle(id, body, ForumActor.from(request)));
    }
}

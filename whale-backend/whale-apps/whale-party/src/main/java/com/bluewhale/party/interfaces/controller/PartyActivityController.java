package com.bluewhale.party.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.activity.application.command.ActivitySaveCommand;
import com.bluewhale.party.activity.application.command.ActivitySigninCommand;
import com.bluewhale.party.activity.application.service.ActivityAppService;
import com.bluewhale.party.activity.application.view.ActivityView;
import com.bluewhale.party.activity.domain.model.PartyActivityQuery;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyPermissions;
import com.bluewhale.party.interfaces.support.PageParams;
import com.bluewhale.party.interfaces.support.PartyActorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 组织生活接口（M-P1）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/party/activity")
@RequiredArgsConstructor
public class PartyActivityController {

    private final ActivityAppService activityAppService;

    /**
     * 组织生活分页。
     *
     * @param keyword 关键字
     * @param branchId 支部 ID
     * @param activityType 类型
     * @param status 状态
     * @param from 计划日期起
     * @param to 计划日期止
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(PartyPermissions.ACTIVITY_LIST)
    public Result<PageResult<ActivityView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "activityType", required = false) String activityType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        PartyActivityQuery query = new PartyActivityQuery();
        query.setKeyword(keyword);
        query.setBranchId(branchId);
        query.setActivityType(activityType);
        query.setStatus(status);
        query.setFrom(from);
        query.setTo(to);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(activityAppService.page(query, actor(request)));
    }

    /**
     * 组织生活详情（含签到明细）。
     *
     * @param id 组织生活 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(PartyPermissions.ACTIVITY_LIST)
    public Result<ActivityView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(activityAppService.detail(id, actor(request)));
    }

    /**
     * 新建组织生活计划。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(PartyPermissions.ACTIVITY_MANAGE)
    @AuditLog(operation = "新建组织生活", type = OperateType.CREATE, targetType = "pty_activity")
    public Result<ActivityView> create(@Valid @RequestBody ActivitySaveCommand command,
                                       HttpServletRequest request) {
        return Result.ok(activityAppService.create(command, actor(request)));
    }

    /**
     * 编辑组织生活。
     *
     * @param id 组织生活 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission(PartyPermissions.ACTIVITY_MANAGE)
    @AuditLog(operation = "编辑组织生活", type = OperateType.UPDATE, targetType = "pty_activity")
    public Result<ActivityView> update(@PathVariable("id") Long id,
                                       @Valid @RequestBody ActivitySaveCommand command,
                                       HttpServletRequest request) {
        return Result.ok(activityAppService.update(id, command, actor(request)));
    }

    /**
     * 标记为已召开。
     *
     * @param id 组织生活 ID
     * @param heldDate 实际召开日期（空表示当天）
     * @param summary 会议纪要
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/complete")
    @RequirePermission(PartyPermissions.ACTIVITY_MANAGE)
    @AuditLog(operation = "标记组织生活完成", type = OperateType.UPDATE, targetType = "pty_activity")
    public Result<ActivityView> complete(
            @PathVariable("id") Long id,
            @RequestParam(value = "heldDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate heldDate,
            @RequestParam(value = "summary", required = false) String summary,
            HttpServletRequest request) {
        return Result.ok(activityAppService.complete(id, heldDate, summary, actor(request)));
    }

    /**
     * 归档组织生活（终态）。
     *
     * @param id 组织生活 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/archive")
    @RequirePermission(PartyPermissions.ACTIVITY_MANAGE)
    @AuditLog(operation = "归档组织生活", type = OperateType.UPDATE, targetType = "pty_activity")
    public Result<ActivityView> archive(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(activityAppService.archive(id, actor(request)));
    }

    /**
     * 整场签到录入。
     *
     * @param id 组织生活 ID
     * @param command 签到命令
     * @param request 当前请求
     * @return 视图（含签到明细）
     */
    @PostMapping("/{id}/signin")
    @RequirePermission(PartyPermissions.ACTIVITY_MANAGE)
    @AuditLog(operation = "组织生活签到", type = OperateType.UPDATE, targetType = "pty_activity_signin")
    public Result<ActivityView> signin(@PathVariable("id") Long id,
                                       @Valid @RequestBody ActivitySigninCommand command,
                                       HttpServletRequest request) {
        return Result.ok(activityAppService.signin(id, command, actor(request)));
    }

    /**
     * 解析操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    private PartyActor actor(HttpServletRequest request) {
        return PartyActorContext.from(request);
    }
}

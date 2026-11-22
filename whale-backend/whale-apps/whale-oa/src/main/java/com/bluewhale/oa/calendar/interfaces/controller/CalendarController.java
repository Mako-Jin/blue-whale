package com.bluewhale.oa.calendar.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.calendar.application.command.CalendarCommands;
import com.bluewhale.oa.calendar.application.service.CalendarAppService;
import com.bluewhale.oa.calendar.application.view.CalendarViews;
import com.bluewhale.oa.calendar.domain.model.CalendarQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OA 日程接口。
 *
 * <p>服务内路径 {@code /oa/calendar/**}，受 {@code sys:oa:calendar:*} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/calendar", version = "1")
@RequiredArgsConstructor
public class CalendarController {

    private static final String TARGET_TYPE = "oa_calendar";

    private final CalendarAppService calendarAppService;

    /**
     * 分页查询当前用户可见日程。
     *
     * @param query   查询条件
     * @param request 请求上下文
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:calendar:list")
    public Result<PageResult<CalendarViews.EntryView>> page(CalendarQuery query, HttpServletRequest request) {
        query.setUserId(UserContext.requireUserId(request));
        return Result.ok(calendarAppService.page(query));
    }

    /**
     * 查询当前用户即将提醒的日程。
     *
     * @param minutes 未来提醒窗口分钟数
     * @param request 请求上下文
     * @return 待提醒日程
     */
    @GetMapping("/reminders")
    @RequirePermission("sys:oa:calendar:reminder")
    public Result<List<CalendarViews.EntryView>> reminders(
            @RequestParam(defaultValue = "30") int minutes, HttpServletRequest request) {
        return Result.ok(calendarAppService.reminders(UserContext.requireUserId(request), minutes));
    }

    /**
     * 日程详情。
     *
     * @param id      日程ID
     * @param request 请求上下文
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:calendar:list")
    public Result<CalendarViews.EntryDetailView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(calendarAppService.detail(id, UserContext.requireUserId(request)));
    }

    /**
     * 新建日程。
     *
     * @param command 保存命令
     * @param request 请求上下文
     * @return 日程
     */
    @PostMapping
    @RequirePermission("sys:oa:calendar:manage")
    @AuditLog(operation = "新建日程", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<CalendarViews.EntryView> create(
            @Valid @RequestBody CalendarCommands.EntrySaveCommand command, HttpServletRequest request) {
        command.setOwnerId(UserContext.requireUserId(request));
        command.setOwnerName(WebHeaders.username(request));
        return Result.ok(calendarAppService.create(command));
    }

    /**
     * 编辑日程。
     *
     * @param id      日程ID
     * @param command 保存命令
     * @param request 请求上下文
     * @return 日程
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:calendar:manage")
    @AuditLog(operation = "编辑日程", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CalendarViews.EntryView> update(
            @PathVariable Long id, @Valid @RequestBody CalendarCommands.EntrySaveCommand command,
            HttpServletRequest request) {
        return Result.ok(calendarAppService.update(id, command, UserContext.requireUserId(request)));
    }

    /**
     * 删除日程。
     *
     * @param id      日程ID
     * @param request 请求上下文
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:calendar:manage")
    @AuditLog(operation = "删除日程", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        calendarAppService.delete(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 替换日程共享名单。
     *
     * @param id      日程ID
     * @param command 共享命令
     * @param request 请求上下文
     * @return 最新共享名单
     */
    @PutMapping("/{id}/shares")
    @RequirePermission("sys:oa:calendar:manage")
    @AuditLog(operation = "维护日程共享", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<List<CalendarViews.ShareView>> replaceShares(
            @PathVariable Long id, @Valid @RequestBody CalendarCommands.ShareSaveCommand command,
            HttpServletRequest request) {
        return Result.ok(calendarAppService.replaceShares(id, command, UserContext.requireUserId(request)));
    }
}

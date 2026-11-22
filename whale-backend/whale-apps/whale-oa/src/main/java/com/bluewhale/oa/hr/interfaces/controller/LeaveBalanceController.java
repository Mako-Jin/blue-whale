package com.bluewhale.oa.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.oa.hr.application.command.LeaveBalanceAdjustCommand;
import com.bluewhale.oa.hr.application.command.LeaveBalanceInitCommand;
import com.bluewhale.oa.hr.application.service.LeaveBalanceAppService;
import com.bluewhale.oa.hr.application.view.LeaveBalanceLogView;
import com.bluewhale.oa.hr.application.view.LeaveBalanceView;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceLogQuery;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * OA 假期额度接口。
 *
 * <p>{@code /page}、{@code /my}、{@code /{id}/logs} 为查询；初始化与调整写操作留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/hr/leave-balance", version = "1")
@RequiredArgsConstructor
public class LeaveBalanceController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_leave_balance";

    private final LeaveBalanceAppService leaveBalanceAppService;

    /**
     * 分页查询假期额度。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:hr:balance:list")
    public Result<PageResult<LeaveBalanceView>> page(LeaveBalanceQuery query) {
        return Result.ok(leaveBalanceAppService.page(query));
    }

    /**
     * 查询当前用户某年度额度。
     *
     * @param year 额度年份
     * @param request 当前请求
     * @return 额度列表
     */
    @GetMapping("/my")
    @RequirePermission("sys:oa:hr:balance:list")
    public Result<List<LeaveBalanceView>> my(@RequestParam Integer year, HttpServletRequest request) {
        return Result.ok(leaveBalanceAppService.myBalance(UserContext.requireUserId(request), year));
    }

    /**
     * 分页查询额度流水。
     *
     * @param id 额度ID
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/{id}/logs")
    @RequirePermission("sys:oa:hr:balance:list")
    public Result<PageResult<LeaveBalanceLogView>> logs(@PathVariable Long id, LeaveBalanceLogQuery query) {
        query.setBalanceId(id);
        return Result.ok(leaveBalanceAppService.logs(query));
    }

    /**
     * 初始化某用户某年度额度（幂等）。
     *
     * @param command 初始化命令
     * @return 初始化后的额度列表
     */
    @PostMapping("/init")
    @RequirePermission("sys:oa:hr:balance:edit")
    @AuditLog(operation = "初始化假期额度", type = OperateType.CREATE, targetType = TARGET_TYPE,
            targetId = "#command.userId")
    public Result<List<LeaveBalanceView>> init(@Valid @RequestBody LeaveBalanceInitCommand command) {
        return Result.ok(leaveBalanceAppService.initForUser(command.getUserId(), command.getYear()));
    }

    /**
     * 调整假期额度总额度。
     *
     * @param id 额度ID
     * @param command 调整命令
     * @return 调整后的额度
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:hr:balance:edit")
    @AuditLog(operation = "调整假期额度", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<LeaveBalanceView> adjust(@PathVariable Long id,
                                           @Valid @RequestBody LeaveBalanceAdjustCommand command) {
        return Result.ok(leaveBalanceAppService.adjust(id, command));
    }
}

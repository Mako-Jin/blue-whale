package com.bluewhale.oa.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.hr.application.command.LeaveSaveCommand;
import com.bluewhale.oa.hr.application.command.LeaveStatusCommand;
import com.bluewhale.oa.hr.application.service.LeaveAppService;
import com.bluewhale.oa.hr.application.view.LeaveView;
import com.bluewhale.oa.hr.domain.model.LeaveQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 请假申请接口。
 *
 * <p>服务内路径 {@code /oa/leave/**}，经网关按 {@code Path=/oa/**} 转发；
 * 全部接口受 {@code sys:oa:leave:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/leave")
@RequiredArgsConstructor
public class LeaveController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_leave";

    private final LeaveAppService leaveAppService;

    /**
     * 分页查询请假申请。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:leave:list")
    public Result<PageResult<LeaveView>> page(LeaveQuery query) {
        return Result.ok(leaveAppService.page(query));
    }

    /**
     * 请假申请详情。
     *
     * @param id 主键ID
     * @return 申请详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:leave:list")
    public Result<LeaveView> detail(@PathVariable Long id) {
        return Result.ok(leaveAppService.detail(id));
    }

    /**
     * 新增请假申请。
     *
     * @param command 新增命令
     * @return 新增后的申请
     */
    @PostMapping
    @RequirePermission("sys:oa:leave:add")
    @AuditLog(operation = "新增请假申请", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<LeaveView> create(@Valid @RequestBody LeaveSaveCommand command) {
        return Result.ok(leaveAppService.create(command));
    }

    /**
     * 更新请假申请。
     *
     * @param id 主键ID
     * @param command 更新命令
     * @return 更新后的申请
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:leave:edit")
    @AuditLog(operation = "更新请假申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<LeaveView> update(@PathVariable Long id, @Valid @RequestBody LeaveSaveCommand command) {
        return Result.ok(leaveAppService.update(id, command));
    }

    /**
     * 审批 / 撤销请假申请。
     *
     * @param id 主键ID
     * @param command 状态变更命令
     * @return 空响应体
     */
    @PutMapping("/{id}/status")
    @RequirePermission("sys:oa:leave:edit")
    @AuditLog(operation = "审批请假申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody LeaveStatusCommand command) {
        leaveAppService.updateStatus(id, command);
        return Result.ok();
    }

    /**
     * 删除请假申请（逻辑删除）。
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:leave:delete")
    @AuditLog(operation = "删除请假申请", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        leaveAppService.delete(id);
        return Result.ok();
    }
}

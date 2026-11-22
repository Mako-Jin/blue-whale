package com.bluewhale.oa.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.hr.application.command.TripReportCommand;
import com.bluewhale.oa.hr.application.command.TripSaveCommand;
import com.bluewhale.oa.hr.application.command.TripStatusCommand;
import com.bluewhale.oa.hr.application.service.TripAppService;
import com.bluewhale.oa.hr.application.view.TripView;
import com.bluewhale.oa.hr.domain.model.TripQuery;
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
 * OA 出差申请接口。
 *
 * <p>服务内路径 {@code /oa/trip/**}，经网关按 {@code Path=/oa/**} 转发；
 * 全部接口受 {@code sys:oa:trip:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/trip", version = "1")
@RequiredArgsConstructor
public class TripController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_trip";

    private final TripAppService tripAppService;

    /**
     * 分页查询出差申请。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:trip:list")
    public Result<PageResult<TripView>> page(TripQuery query) {
        return Result.ok(tripAppService.page(query));
    }

    /**
     * 出差申请详情（含行程与预支）。
     *
     * @param id 主键ID
     * @return 申请详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:trip:list")
    public Result<TripView> detail(@PathVariable Long id) {
        return Result.ok(tripAppService.detail(id));
    }

    /**
     * 新增出差申请。
     *
     * @param command 新增命令
     * @return 新增后的申请
     */
    @PostMapping
    @RequirePermission("sys:oa:trip:add")
    @AuditLog(operation = "新增出差申请", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<TripView> create(@Valid @RequestBody TripSaveCommand command) {
        return Result.ok(tripAppService.create(command));
    }

    /**
     * 更新出差申请。
     *
     * @param id 主键ID
     * @param command 更新命令
     * @return 更新后的申请
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:trip:edit")
    @AuditLog(operation = "更新出差申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<TripView> update(@PathVariable Long id, @Valid @RequestBody TripSaveCommand command) {
        return Result.ok(tripAppService.update(id, command));
    }

    /**
     * 审批 / 撤销出差申请。
     *
     * @param id 主键ID
     * @param command 状态变更命令
     * @return 空响应体
     */
    @PutMapping("/{id}/status")
    @RequirePermission("sys:oa:trip:edit")
    @AuditLog(operation = "审批出差申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody TripStatusCommand command) {
        tripAppService.updateStatus(id, command);
        return Result.ok();
    }

    /**
     * 提交出差报告。
     *
     * @param id 主键ID
     * @param command 报告命令
     * @return 空响应体
     */
    @PostMapping("/{id}/report")
    @RequirePermission("sys:oa:trip:report")
    @AuditLog(operation = "提交出差报告", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> submitReport(@PathVariable Long id, @Valid @RequestBody TripReportCommand command) {
        tripAppService.submitReport(id, command);
        return Result.ok();
    }

    /**
     * 删除出差申请（逻辑删除）。
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:trip:delete")
    @AuditLog(operation = "删除出差申请", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        tripAppService.delete(id);
        return Result.ok();
    }
}

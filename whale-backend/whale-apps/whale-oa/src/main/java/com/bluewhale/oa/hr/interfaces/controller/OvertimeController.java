package com.bluewhale.oa.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.hr.application.command.OvertimeSaveCommand;
import com.bluewhale.oa.hr.application.command.OvertimeStatusCommand;
import com.bluewhale.oa.hr.application.service.OvertimeAppService;
import com.bluewhale.oa.hr.application.view.OvertimeView;
import com.bluewhale.oa.hr.domain.model.OvertimeQuery;
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
 * OA 加班申请接口。
 *
 * <p>服务内路径 {@code /oa/overtime/**}，经网关按 {@code Path=/oa/**} 转发；
 * 全部接口受 {@code sys:oa:overtime:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/overtime")
@RequiredArgsConstructor
public class OvertimeController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_overtime";

    private final OvertimeAppService overtimeAppService;

    /**
     * 分页查询加班申请。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:overtime:list")
    public Result<PageResult<OvertimeView>> page(OvertimeQuery query) {
        return Result.ok(overtimeAppService.page(query));
    }

    /**
     * 加班申请详情。
     *
     * @param id 主键ID
     * @return 申请详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:overtime:list")
    public Result<OvertimeView> detail(@PathVariable Long id) {
        return Result.ok(overtimeAppService.detail(id));
    }

    /**
     * 新增加班申请。
     *
     * @param command 新增命令
     * @return 新增后的申请
     */
    @PostMapping
    @RequirePermission("sys:oa:overtime:add")
    @AuditLog(operation = "新增加班申请", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<OvertimeView> create(@Valid @RequestBody OvertimeSaveCommand command) {
        return Result.ok(overtimeAppService.create(command));
    }

    /**
     * 更新加班申请。
     *
     * @param id 主键ID
     * @param command 更新命令
     * @return 更新后的申请
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:overtime:edit")
    @AuditLog(operation = "更新加班申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<OvertimeView> update(@PathVariable Long id, @Valid @RequestBody OvertimeSaveCommand command) {
        return Result.ok(overtimeAppService.update(id, command));
    }

    /**
     * 审批 / 撤销加班申请。
     *
     * @param id 主键ID
     * @param command 状态变更命令
     * @return 空响应体
     */
    @PutMapping("/{id}/status")
    @RequirePermission("sys:oa:overtime:edit")
    @AuditLog(operation = "审批加班申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody OvertimeStatusCommand command) {
        overtimeAppService.updateStatus(id, command);
        return Result.ok();
    }

    /**
     * 删除加班申请（逻辑删除）。
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:overtime:delete")
    @AuditLog(operation = "删除加班申请", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        overtimeAppService.delete(id);
        return Result.ok();
    }
}

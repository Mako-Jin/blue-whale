package com.bluewhale.oa.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.hr.application.command.LeaveTypeSaveCommand;
import com.bluewhale.oa.hr.application.service.LeaveTypeAppService;
import com.bluewhale.oa.hr.application.view.LeaveTypeView;
import com.bluewhale.oa.hr.domain.model.LeaveTypeQuery;
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
 * OA 假期类型接口。
 *
 * <p>管理员维护假期类型编码、额度与是否允许负额度；启用类型参与年度额度初始化。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/hr/leave-type", version = "1")
@RequiredArgsConstructor
public class LeaveTypeController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_leave_type";

    private final LeaveTypeAppService leaveTypeAppService;

    /**
     * 分页查询假期类型。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:hr:leave-type:list")
    public Result<PageResult<LeaveTypeView>> page(LeaveTypeQuery query) {
        return Result.ok(leaveTypeAppService.page(query));
    }

    /**
     * 查询全部启用假期类型。
     *
     * @return 启用类型列表
     */
    @GetMapping("/enabled")
    @RequirePermission("sys:oa:hr:leave-type:list")
    public Result<List<LeaveTypeView>> enabled() {
        return Result.ok(leaveTypeAppService.listEnabled());
    }

    /**
     * 新增假期类型。
     *
     * @param command 保存命令
     * @return 新增后的类型
     */
    @PostMapping
    @RequirePermission("sys:oa:hr:leave-type:edit")
    @AuditLog(operation = "新增假期类型", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<LeaveTypeView> create(@Valid @RequestBody LeaveTypeSaveCommand command) {
        return Result.ok(leaveTypeAppService.create(command));
    }

    /**
     * 更新假期类型。
     *
     * @param id 主键ID
     * @param command 保存命令
     * @return 更新后的类型
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:hr:leave-type:edit")
    @AuditLog(operation = "更新假期类型", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<LeaveTypeView> update(@PathVariable Long id,
                                        @Valid @RequestBody LeaveTypeSaveCommand command) {
        return Result.ok(leaveTypeAppService.update(id, command));
    }

    /**
     * 启用 / 停用假期类型。
     *
     * @param id 主键ID
     * @param enabled 是否启用
     * @return 更新后的类型
     */
    @PutMapping("/{id}/toggle")
    @RequirePermission("sys:oa:hr:leave-type:edit")
    @AuditLog(operation = "启停假期类型", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<LeaveTypeView> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.ok(leaveTypeAppService.toggle(id, enabled));
    }
}

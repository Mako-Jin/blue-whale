package com.bluewhale.oa.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.hr.application.command.EmployeeProfileSaveCommand;
import com.bluewhale.oa.hr.application.service.EmployeeProfileAppService;
import com.bluewhale.oa.hr.application.view.EmployeeProfileView;
import com.bluewhale.oa.hr.domain.model.EmployeeProfileQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 员工扩展档案接口。
 *
 * <p>按用户维度维护工号、入离职日期、生日与文化墙展示开关，基础身份信息仍由 Auth 提供。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/hr/profile", version = "1")
@RequiredArgsConstructor
public class EmployeeProfileController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_employee_profile";

    private final EmployeeProfileAppService employeeProfileAppService;

    /**
     * 分页查询员工档案。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:hr:profile:list")
    public Result<PageResult<EmployeeProfileView>> page(EmployeeProfileQuery query) {
        return Result.ok(employeeProfileAppService.page(query));
    }

    /**
     * 按用户ID查询档案。
     *
     * @param userId 用户ID
     * @return 档案详情
     */
    @GetMapping("/{userId}")
    @RequirePermission("sys:oa:hr:profile:list")
    public Result<EmployeeProfileView> detail(@PathVariable Long userId) {
        return Result.ok(employeeProfileAppService.findByUserId(userId));
    }

    /**
     * 保存员工档案（按用户ID 幂等 upsert）。
     *
     * @param command 保存命令
     * @return 保存后的档案
     */
    @PostMapping
    @RequirePermission("sys:oa:hr:profile:edit")
    @AuditLog(operation = "保存员工档案", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#command.userId")
    public Result<EmployeeProfileView> save(@Valid @RequestBody EmployeeProfileSaveCommand command) {
        return Result.ok(employeeProfileAppService.save(command));
    }

    /**
     * 删除员工档案。
     *
     * @param userId 用户ID
     * @return 空响应体
     */
    @DeleteMapping("/{userId}")
    @RequirePermission("sys:oa:hr:profile:edit")
    @AuditLog(operation = "删除员工档案", type = OperateType.DELETE, targetType = TARGET_TYPE,
            targetId = "#userId")
    public Result<Void> delete(@PathVariable Long userId) {
        employeeProfileAppService.delete(userId);
        return Result.ok();
    }
}

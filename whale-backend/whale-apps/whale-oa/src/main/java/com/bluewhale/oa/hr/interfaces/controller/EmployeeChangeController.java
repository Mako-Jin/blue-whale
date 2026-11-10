package com.bluewhale.oa.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.hr.application.command.EmployeeChangeCommands;
import com.bluewhale.oa.hr.application.service.EmployeeChangeAppService;
import com.bluewhale.oa.hr.application.view.EmployeeChangeViews;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 人员异动接口。
 *
 * <p>覆盖入职、转正、调岗、离职、返聘的申请、审批与执行；审批通过后同步 Auth 与员工档案，
 * 离职自动生成清单任务。查看权限 {@code sys:oa:hr:change:list}，维护权限
 * {@code sys:oa:hr:change:manage}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/hr/change")
@RequiredArgsConstructor
public class EmployeeChangeController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_employee_change";

    private final EmployeeChangeAppService employeeChangeAppService;

    /**
     * 分页查询人员异动单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:hr:change:list")
    public Result<PageResult<EmployeeChangeViews.ListItem>> page(EmployeeChangeQuery query) {
        return Result.ok(employeeChangeAppService.page(query));
    }

    /**
     * 查询异动单详情（含离职清单任务）。
     *
     * @param id 异动单ID
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:hr:change:list")
    public Result<EmployeeChangeViews.Detail> detail(@PathVariable Long id) {
        return Result.ok(employeeChangeAppService.detail(id));
    }

    /**
     * 新建人员异动单。
     *
     * @param command 保存命令
     * @param request 请求上下文
     * @return 详情
     */
    @PostMapping
    @RequirePermission("sys:oa:hr:change:manage")
    @AuditLog(operation = "新建人员异动", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<EmployeeChangeViews.Detail> create(
            @Valid @RequestBody EmployeeChangeCommands.Save command, HttpServletRequest request) {
        return Result.ok(employeeChangeAppService.create(command,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 编辑草稿/驳回/执行失败的异动单。
     *
     * @param id 异动单ID
     * @param command 保存命令
     * @return 详情
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:hr:change:manage")
    @AuditLog(operation = "编辑人员异动", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#id")
    public Result<EmployeeChangeViews.Detail> update(
            @PathVariable Long id, @Valid @RequestBody EmployeeChangeCommands.Save command) {
        return Result.ok(employeeChangeAppService.update(id, command));
    }

    /**
     * 提交审批。
     *
     * @param id 异动单ID
     * @return 详情
     */
    @PostMapping("/{id}/submit")
    @RequirePermission("sys:oa:hr:change:manage")
    @AuditLog(operation = "提交人员异动审批", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#id")
    public Result<EmployeeChangeViews.Detail> submit(@PathVariable Long id) {
        return Result.ok(employeeChangeAppService.submit(id));
    }

    /**
     * 手工执行审批通过的人员异动（四眼校验）。
     *
     * @param id 异动单ID
     * @param request 请求上下文
     * @return 执行结果
     */
    @PostMapping("/{id}/execute")
    @RequirePermission("sys:oa:hr:change:manage")
    @AuditLog(operation = "执行人员异动", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#id")
    public Result<EmployeeChangeViews.ExecuteResult> execute(@PathVariable Long id,
                                                            HttpServletRequest request) {
        return Result.ok(employeeChangeAppService.execute(id,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 完成离职清单任务。
     *
     * @param id 异动单ID
     * @param taskId 任务ID
     * @param command 完成命令
     * @return 空结果
     */
    @PutMapping("/{id}/tasks/{taskId}/done")
    @RequirePermission("sys:oa:hr:change:manage")
    @AuditLog(operation = "完成人员异动任务", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#id")
    public Result<Void> doneTask(@PathVariable Long id, @PathVariable Long taskId,
                                 @RequestBody(required = false) EmployeeChangeCommands.TaskDone command) {
        employeeChangeAppService.doneTask(id, taskId, command == null ? null : command.getRemark());
        return Result.ok();
    }
}

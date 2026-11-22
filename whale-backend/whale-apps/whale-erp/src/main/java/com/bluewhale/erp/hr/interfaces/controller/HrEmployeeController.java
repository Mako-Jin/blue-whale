package com.bluewhale.erp.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.hr.application.command.EmployeeCreateCommand;
import com.bluewhale.erp.hr.application.command.EmployeeUpdateCommand;
import com.bluewhale.erp.hr.application.command.EmploymentChangeCreateCommand;
import com.bluewhale.erp.hr.application.service.EmployeeAppService;
import com.bluewhale.erp.hr.application.view.EmployeeView;
import com.bluewhale.erp.hr.application.view.EmploymentChangeView;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.interfaces.support.PageParams;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工档案与异动接口：建档、编辑、异动单与生效。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/hr/employee", version = "1")
@RequiredArgsConstructor
public class HrEmployeeController {

    private final EmployeeAppService employeeAppService;

    /**
     * 员工分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 员工分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:hr:employee:list")
    public Result<PageResult<EmployeeView>> page(
            @ModelAttribute HrQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(employeeAppService.page(query));
    }

    /**
     * 员工详情。
     *
     * @param employeeNo 员工编号
     * @return 员工视图
     */
    @GetMapping("/{employeeNo}")
    @RequirePermission("sys:erp:hr:employee:list")
    public Result<EmployeeView> detail(@PathVariable String employeeNo) {
        return Result.ok(employeeAppService.detail(employeeNo));
    }

    /**
     * 新增员工。
     *
     * @param command 创建命令
     * @return 员工视图
     */
    @PostMapping
    @RequirePermission("sys:erp:hr:employee:add")
    @AuditLog(operation = "新增员工档案", targetType = "erp_hr_employee", type = OperateType.CREATE)
    public Result<EmployeeView> create(@Valid @RequestBody EmployeeCreateCommand command) {
        return Result.ok(employeeAppService.create(command));
    }

    /**
     * 编辑员工档案。
     *
     * @param employeeNo 员工编号
     * @param command 编辑命令
     * @return 员工视图
     */
    @PutMapping("/{employeeNo}")
    @RequirePermission("sys:erp:hr:employee:update")
    @AuditLog(operation = "编辑员工档案", targetType = "erp_hr_employee", type = OperateType.UPDATE)
    public Result<EmployeeView> update(@PathVariable String employeeNo,
                                       @Valid @RequestBody EmployeeUpdateCommand command) {
        return Result.ok(employeeAppService.update(employeeNo, command));
    }

    /**
     * 异动单分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 异动单分页
     */
    @GetMapping("/change/page")
    @RequirePermission("sys:erp:hr:employee:list")
    public Result<PageResult<EmploymentChangeView>> pageChanges(
            @ModelAttribute HrQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(employeeAppService.pageChanges(query));
    }

    /**
     * 新增异动单（草稿）。
     *
     * @param command 创建命令
     * @return 异动单视图
     */
    @PostMapping("/change")
    @RequirePermission("sys:erp:hr:employee:change")
    @AuditLog(operation = "新增员工异动", targetType = "erp_hr_employment_change", type = OperateType.CREATE)
    public Result<EmploymentChangeView> createChange(@Valid @RequestBody EmploymentChangeCreateCommand command) {
        return Result.ok(employeeAppService.createChange(command));
    }

    /**
     * 异动生效。
     *
     * @param id 异动单主键
     * @return 异动单视图
     */
    @PostMapping("/change/{id}/effective")
    @RequirePermission("sys:erp:hr:employee:change")
    @AuditLog(operation = "员工异动生效", targetType = "erp_hr_employment_change", type = OperateType.UPDATE)
    public Result<EmploymentChangeView> effective(@PathVariable Long id) {
        return Result.ok(employeeAppService.effectiveChange(id));
    }
}

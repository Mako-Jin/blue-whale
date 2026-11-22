package com.bluewhale.erp.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.hr.application.command.PayrollCreateCommand;
import com.bluewhale.erp.hr.application.service.PayrollAppService;
import com.bluewhale.erp.hr.application.view.PayrollRunView;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.interfaces.support.ErpUserContext;
import com.bluewhale.erp.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 薪资批次接口：新建、计算、审批、发放、结账、取消与我的工资条。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/hr/payroll", version = "1")
@RequiredArgsConstructor
public class HrPayrollController {

    private final PayrollAppService payrollAppService;

    /**
     * 薪资批次分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 批次分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:hr:payroll:list")
    public Result<PageResult<PayrollRunView>> page(
            @ModelAttribute HrQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(payrollAppService.page(query));
    }

    /**
     * 批次详情（含工资条）。
     *
     * @param id 批次主键
     * @return 批次视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:hr:payroll:list")
    public Result<PayrollRunView> detail(@PathVariable Long id) {
        return Result.ok(payrollAppService.detail(id));
    }

    /**
     * 新建薪资批次。
     *
     * @param command 创建命令
     * @return 批次视图
     */
    @PostMapping
    @RequirePermission("sys:erp:hr:payroll:create")
    @AuditLog(operation = "新建薪资批次", targetType = "erp_hr_payroll_run", type = OperateType.CREATE)
    public Result<PayrollRunView> create(@Valid @RequestBody PayrollCreateCommand command) {
        return Result.ok(payrollAppService.create(command));
    }

    /**
     * 计算薪资并生成工资条。
     *
     * @param id 批次主键
     * @return 批次视图
     */
    @PostMapping("/{id}/calculate")
    @RequirePermission("sys:erp:hr:payroll:calculate")
    @AuditLog(operation = "计算薪资", targetType = "erp_hr_payroll_run", type = OperateType.UPDATE)
    public Result<PayrollRunView> calculate(@PathVariable Long id) {
        return Result.ok(payrollAppService.calculate(id));
    }

    /**
     * 审批薪资（生成计提凭证）。
     *
     * @param id 批次主键
     * @param request HTTP 请求（取操作人）
     * @return 批次视图
     */
    @PostMapping("/{id}/approve")
    @RequirePermission("sys:erp:hr:payroll:approve")
    @AuditLog(operation = "审批薪资", targetType = "erp_hr_payroll_run", type = OperateType.UPDATE)
    public Result<PayrollRunView> approve(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(payrollAppService.approve(id, ErpUserContext.userId(request)));
    }

    /**
     * 发放薪资（生成发放凭证）。
     *
     * @param id 批次主键
     * @param request HTTP 请求（取操作人）
     * @return 批次视图
     */
    @PostMapping("/{id}/pay")
    @RequirePermission("sys:erp:hr:payroll:pay")
    @AuditLog(operation = "发放薪资", targetType = "erp_hr_payroll_run", type = OperateType.UPDATE)
    public Result<PayrollRunView> pay(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(payrollAppService.pay(id, ErpUserContext.userId(request)));
    }

    /**
     * 结账。
     *
     * @param id 批次主键
     * @return 批次视图
     */
    @PostMapping("/{id}/close")
    @RequirePermission("sys:erp:hr:payroll:approve")
    @AuditLog(operation = "薪资批次结账", targetType = "erp_hr_payroll_run", type = OperateType.UPDATE)
    public Result<PayrollRunView> close(@PathVariable Long id) {
        return Result.ok(payrollAppService.close(id));
    }

    /**
     * 取消批次。
     *
     * @param id 批次主键
     * @return 批次视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:erp:hr:payroll:create")
    @AuditLog(operation = "取消薪资批次", targetType = "erp_hr_payroll_run", type = OperateType.UPDATE)
    public Result<PayrollRunView> cancel(@PathVariable Long id) {
        return Result.ok(payrollAppService.cancel(id));
    }

    /**
     * 我的工资条（仅本人可见）。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request HTTP 请求（取当前账号）
     * @return 本人工资条分页
     */
    @GetMapping("/payslip/my")
    @RequirePermission("sys:erp:hr:payslip:list")
    public Result<PageResult<PayrollRunView>> myPayslips(
            @ModelAttribute HrQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(payrollAppService.myPayslips(ErpUserContext.userId(request), query));
    }
}

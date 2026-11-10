package com.bluewhale.oa.expense.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.expense.application.command.ExpenseCommands;
import com.bluewhale.oa.expense.application.service.ExpenseAppService;
import com.bluewhale.oa.expense.application.view.ExpenseViews;
import com.bluewhale.oa.expense.domain.model.ExpenseQuery;
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
 * OA 报销单接口。
 *
 * <p>服务内路径 {@code /oa/expense/**}，经网关按 {@code Path=/oa/**} 转发；
 * 全部接口受 {@code sys:oa:expense:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/expense")
@RequiredArgsConstructor
public class ExpenseController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_expense";

    private final ExpenseAppService expenseAppService;

    /**
     * 分页查询报销单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:expense:list")
    public Result<PageResult<ExpenseViews.ExpenseView>> page(ExpenseQuery query) {
        return Result.ok(expenseAppService.page(query));
    }

    /**
     * 报销单详情（含明细与发票）。
     *
     * @param id 主键ID
     * @return 报销单详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:expense:list")
    public Result<ExpenseViews.ExpenseView> detail(@PathVariable Long id) {
        return Result.ok(expenseAppService.detail(id));
    }

    /**
     * 新建报销单。
     *
     * @param command 保存命令
     * @return 报销单详情
     */
    @PostMapping
    @RequirePermission("sys:oa:expense:add")
    @AuditLog(operation = "新建报销单", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ExpenseViews.ExpenseView> create(
            @Valid @RequestBody ExpenseCommands.ExpenseSaveCommand command) {
        return Result.ok(expenseAppService.create(command));
    }

    /**
     * 更新报销单。
     *
     * @param id 主键ID
     * @param command 保存命令
     * @return 报销单详情
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:expense:edit")
    @AuditLog(operation = "更新报销单", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ExpenseViews.ExpenseView> update(@PathVariable Long id,
                                                   @Valid @RequestBody ExpenseCommands.ExpenseSaveCommand command) {
        return Result.ok(expenseAppService.update(id, command));
    }

    /**
     * 删除报销单（仅待审批）。
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:expense:delete")
    @AuditLog(operation = "删除报销单", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        expenseAppService.delete(id);
        return Result.ok();
    }

    /**
     * 提交报销单（幂等登记审批实例）。
     *
     * @param id 主键ID
     * @return 报销单详情
     */
    @PostMapping("/{id}/submit")
    @RequirePermission("sys:oa:expense:submit")
    @AuditLog(operation = "提交报销单", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ExpenseViews.ExpenseView> submit(@PathVariable Long id) {
        return Result.ok(expenseAppService.submit(id));
    }

    /**
     * 审批 / 撤销报销单。
     *
     * @param id 主键ID
     * @param command 状态变更命令
     * @return 空响应体
     */
    @PutMapping("/{id}/status")
    @RequirePermission("sys:oa:expense:submit")
    @AuditLog(operation = "变更报销单状态", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody ExpenseCommands.ExpenseStatusCommand command) {
        expenseAppService.updateStatus(id, command);
        return Result.ok();
    }

    /**
     * 手工重试推送 ERP 应付单。
     *
     * @param id 主键ID
     * @return 报销单详情
     */
    @PostMapping("/{id}/push-retry")
    @RequirePermission("sys:oa:expense:push")
    @AuditLog(operation = "重试推送报销应付", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ExpenseViews.ExpenseView> retryPush(@PathVariable Long id) {
        return Result.ok(expenseAppService.retryPush(id));
    }
}

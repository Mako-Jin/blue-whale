package com.bluewhale.oa.expense.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.expense.application.command.ExpenseCommands;
import com.bluewhale.oa.expense.application.service.ExpenseCategoryAppService;
import com.bluewhale.oa.expense.application.view.ExpenseViews;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OA 费用类别与费用标准接口。
 *
 * <p>服务内路径 {@code /oa/expense/category/**}，配置类操作受
 * {@code sys:oa:expense:category:*} 权限保护并留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/expense/category", version = "1")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_expense_category";

    private final ExpenseCategoryAppService expenseCategoryAppService;

    /**
     * 查询全部费用类别。
     *
     * @return 费用类别列表
     */
    @GetMapping
    @RequirePermission("sys:oa:expense:category:list")
    public Result<List<ExpenseViews.CategoryView>> list() {
        return Result.ok(expenseCategoryAppService.listCategories());
    }

    /**
     * 新增或更新费用类别。
     *
     * @param command 保存命令
     * @return 费用类别
     */
    @PostMapping
    @RequirePermission("sys:oa:expense:category:edit")
    @AuditLog(operation = "保存费用类别", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#command.id")
    public Result<ExpenseViews.CategoryView> save(
            @Valid @RequestBody ExpenseCommands.CategorySaveCommand command) {
        return Result.ok(expenseCategoryAppService.saveCategory(command));
    }

    /**
     * 删除费用类别。
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:expense:category:edit")
    @AuditLog(operation = "删除费用类别", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        expenseCategoryAppService.deleteCategory(id);
        return Result.ok();
    }

    /**
     * 查询指定类别的费用标准。
     *
     * @param categoryId 费用类别ID
     * @return 费用标准列表
     */
    @GetMapping("/{categoryId}/standards")
    @RequirePermission("sys:oa:expense:category:list")
    public Result<List<ExpenseViews.StandardView>> standards(@PathVariable Long categoryId) {
        return Result.ok(expenseCategoryAppService.listStandards(categoryId));
    }

    /**
     * 新增或更新费用标准。
     *
     * @param command 保存命令
     * @return 费用标准
     */
    @PostMapping("/standards")
    @RequirePermission("sys:oa:expense:category:edit")
    @AuditLog(operation = "保存费用标准", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#command.id")
    public Result<ExpenseViews.StandardView> saveStandard(
            @Valid @RequestBody ExpenseCommands.StandardSaveCommand command) {
        return Result.ok(expenseCategoryAppService.saveStandard(command));
    }

    /**
     * 删除费用标准。
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/standards/{id}")
    @RequirePermission("sys:oa:expense:category:edit")
    @AuditLog(operation = "删除费用标准", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> deleteStandard(@PathVariable Long id) {
        expenseCategoryAppService.deleteStandard(id);
        return Result.ok();
    }
}

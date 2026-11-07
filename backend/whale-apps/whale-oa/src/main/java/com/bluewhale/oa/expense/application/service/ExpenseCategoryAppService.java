package com.bluewhale.oa.expense.application.service;

import com.bluewhale.oa.expense.application.command.ExpenseCommands;
import com.bluewhale.oa.expense.application.view.ExpenseViews;

import java.util.List;

/**
 * 费用类别与费用标准应用服务。
 *
 * @author BlueWhale
 */
public interface ExpenseCategoryAppService {

    /**
     * 查询全部费用类别。
     *
     * @return 费用类别列表
     */
    List<ExpenseViews.CategoryView> listCategories();

    /**
     * 新增或更新费用类别。
     *
     * @param command 保存命令
     * @return 费用类别
     */
    ExpenseViews.CategoryView saveCategory(ExpenseCommands.CategorySaveCommand command);

    /**
     * 删除费用类别。
     *
     * @param id 主键ID
     */
    void deleteCategory(Long id);

    /**
     * 查询指定类别的费用标准。
     *
     * @param categoryId 费用类别ID
     * @return 费用标准列表
     */
    List<ExpenseViews.StandardView> listStandards(Long categoryId);

    /**
     * 新增或更新费用标准。
     *
     * @param command 保存命令
     * @return 费用标准
     */
    ExpenseViews.StandardView saveStandard(ExpenseCommands.StandardSaveCommand command);

    /**
     * 删除费用标准。
     *
     * @param id 主键ID
     */
    void deleteStandard(Long id);
}

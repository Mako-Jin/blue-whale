package com.bluewhale.oa.expense.domain.repository;

import com.bluewhale.oa.expense.domain.model.ExpenseCategory;
import com.bluewhale.oa.expense.domain.model.ExpenseStandard;

import java.util.List;
import java.util.Optional;

/**
 * 费用类别与费用标准仓储端口。
 *
 * @author BlueWhale
 */
public interface ExpenseCategoryRepository {

    /**
     * 查询全部费用类别（按排序号）。
     *
     * @return 费用类别列表
     */
    List<ExpenseCategory> listCategories();

    /**
     * 按主键查询费用类别。
     *
     * @param id 主键ID
     * @return 费用类别
     */
    Optional<ExpenseCategory> findCategoryById(Long id);

    /**
     * 按编码查询费用类别。
     *
     * @param code 类别编码
     * @return 费用类别
     */
    Optional<ExpenseCategory> findCategoryByCode(String code);

    /**
     * 保存费用类别（新增或更新）。
     *
     * @param category 费用类别
     */
    void saveCategory(ExpenseCategory category);

    /**
     * 逻辑删除费用类别。
     *
     * @param id 主键ID
     */
    void deleteCategory(Long id);

    /**
     * 判断费用类别编码是否已存在。
     *
     * @param code 类别编码
     * @param excludeId 需排除的主键ID
     * @return 是否已存在
     */
    boolean existsCategoryCode(String code, Long excludeId);

    /**
     * 查询指定类别的费用标准。
     *
     * @param categoryId 费用类别ID
     * @return 费用标准列表
     */
    List<ExpenseStandard> listStandards(Long categoryId);

    /**
     * 按类别与适用范围查询费用标准。
     *
     * @param categoryId 费用类别ID
     * @param levelCode 适用职级/城市编码
     * @return 费用标准
     */
    Optional<ExpenseStandard> findStandard(Long categoryId, String levelCode);

    /**
     * 按主键查询费用标准。
     *
     * @param id 主键ID
     * @return 费用标准
     */
    Optional<ExpenseStandard> findStandardById(Long id);

    /**
     * 保存费用标准（新增或更新）。
     *
     * @param standard 费用标准
     */
    void saveStandard(ExpenseStandard standard);

    /**
     * 逻辑删除费用标准。
     *
     * @param id 主键ID
     */
    void deleteStandard(Long id);
}

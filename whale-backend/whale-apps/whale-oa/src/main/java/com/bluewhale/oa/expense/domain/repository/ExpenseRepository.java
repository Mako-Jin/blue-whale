package com.bluewhale.oa.expense.domain.repository;

import com.bluewhale.oa.expense.domain.model.Expense;
import com.bluewhale.oa.expense.domain.model.ExpenseInvoice;
import com.bluewhale.oa.expense.domain.model.ExpenseItem;
import com.bluewhale.oa.expense.domain.model.ExpenseQuery;

import java.util.List;
import java.util.Optional;

/**
 * 报销单仓储端口（含明细与发票）。
 *
 * @author BlueWhale
 */
public interface ExpenseRepository {

    /**
     * 分页查询报销单（仅单头）。
     *
     * @param query 查询条件
     * @return 报销单列表
     */
    List<Expense> page(ExpenseQuery query);

    /**
     * 统计报销单数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ExpenseQuery query);

    /**
     * 按主键查询报销单（仅单头）。
     *
     * @param id 主键ID
     * @return 报销单
     */
    Optional<Expense> findById(Long id);

    /**
     * 判断单号是否已存在。
     *
     * @param code 单号
     * @param excludeId 需排除的主键ID
     * @return 是否已存在
     */
    boolean existsByCode(String code, Long excludeId);

    /**
     * 新增报销单。
     *
     * @param expense 报销单
     */
    void insert(Expense expense);

    /**
     * 更新报销单。
     *
     * @param expense 报销单
     */
    void update(Expense expense);

    /**
     * 逻辑删除报销单。
     *
     * @param id 主键ID
     */
    void deleteById(Long id);

    /**
     * 查询报销明细。
     *
     * @param expenseId 报销单ID
     * @return 明细列表
     */
    List<ExpenseItem> listItems(Long expenseId);

    /**
     * 整单替换报销明细。
     *
     * @param expenseId 报销单ID
     * @param items 明细列表
     */
    void replaceItems(Long expenseId, List<ExpenseItem> items);

    /**
     * 查询报销发票。
     *
     * @param expenseId 报销单ID
     * @return 发票列表
     */
    List<ExpenseInvoice> listInvoices(Long expenseId);

    /**
     * 整单替换报销发票。
     *
     * @param expenseId 报销单ID
     * @param invoices 发票列表
     */
    void replaceInvoices(Long expenseId, List<ExpenseInvoice> invoices);

    /**
     * 判断发票是否已被其他报销单使用。
     *
     * @param invoiceCode 发票代码
     * @param invoiceNo 发票号码
     * @param excludeExpenseId 需排除的报销单ID
     * @return 是否已存在
     */
    boolean existsInvoice(String invoiceCode, String invoiceNo, Long excludeExpenseId);

    /**
     * 查询待推送 ERP 的报销单（已通过且推送状态为待推送/失败）。
     *
     * @param limit 最大条数
     * @return 报销单列表
     */
    List<Expense> listPushable(int limit);
}

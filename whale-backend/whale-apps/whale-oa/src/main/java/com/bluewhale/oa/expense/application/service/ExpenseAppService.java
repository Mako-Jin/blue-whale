package com.bluewhale.oa.expense.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.expense.application.command.ExpenseCommands;
import com.bluewhale.oa.expense.application.view.ExpenseViews;
import com.bluewhale.oa.expense.domain.model.ExpenseQuery;

/**
 * 报销单应用服务。
 *
 * @author BlueWhale
 */
public interface ExpenseAppService {

    /**
     * 分页查询报销单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ExpenseViews.ExpenseView> page(ExpenseQuery query);

    /**
     * 查询报销单详情（含明细与发票）。
     *
     * @param id 主键ID
     * @return 报销单详情
     */
    ExpenseViews.ExpenseView detail(Long id);

    /**
     * 新建报销单（服务端重算合计并校验标准，登记审批实例）。
     *
     * @param command 保存命令
     * @return 报销单详情
     */
    ExpenseViews.ExpenseView create(ExpenseCommands.ExpenseSaveCommand command);

    /**
     * 更新报销单（仅待审批可更新）。
     *
     * @param id 主键ID
     * @param command 保存命令
     * @return 报销单详情
     */
    ExpenseViews.ExpenseView update(Long id, ExpenseCommands.ExpenseSaveCommand command);

    /**
     * 删除报销单（仅待审批可删除）。
     *
     * @param id 主键ID
     */
    void delete(Long id);

    /**
     * 提交报销单（幂等登记审批实例）。
     *
     * @param id 主键ID
     * @return 报销单详情
     */
    ExpenseViews.ExpenseView submit(Long id);

    /**
     * 变更报销单状态（审批结果回写复用）。
     *
     * @param id 主键ID
     * @param command 状态命令
     */
    void updateStatus(Long id, ExpenseCommands.ExpenseStatusCommand command);

    /**
     * 同步审批终态结果（幂等）。
     *
     * @param expenseId 报销单ID
     * @param result 审批结果
     */
    void syncApprovalResult(Long expenseId, String result);

    /**
     * 批量推送待推送的报销单到 ERP（失败不抛出，记录 FAILED）。
     *
     * @return 成功推送条数
     */
    int pushPending();

    /**
     * 手工重试推送单张报销单到 ERP。
     *
     * @param id 主键ID
     * @return 报销单详情
     */
    ExpenseViews.ExpenseView retryPush(Long id);
}

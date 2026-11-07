package com.bluewhale.crm.commerce.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.commerce.application.command.InvoiceApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceIssueCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceRedFlushCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceSaveCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceSendCommand;
import com.bluewhale.crm.commerce.application.view.InvoiceView;
import com.bluewhale.crm.commerce.domain.model.InvoiceQuery;

/**
 * 开票申请应用服务（M3 / 7f-3）。
 *
 * @author BlueWhale
 */
public interface InvoiceAppService {

    /**
     * 分页查询开票申请。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页
     */
    PageResult<InvoiceView> page(InvoiceQuery query, CrmActor actor);

    /**
     * 查询开票申请详情。
     *
     * @param id 申请 ID
     * @param actor 当前操作人
     * @return 详情
     */
    InvoiceView detail(Long id, CrmActor actor);

    /**
     * 新建开票申请。
     *
     * @param command 新建命令
     * @param actor 当前操作人
     * @return 视图
     */
    InvoiceView create(InvoiceSaveCommand command, CrmActor actor);

    /**
     * 编辑开票申请（仅草稿）。
     *
     * @param id 申请 ID
     * @param command 编辑命令
     * @param actor 当前操作人
     * @return 视图
     */
    InvoiceView update(Long id, InvoiceSaveCommand command, CrmActor actor);

    /**
     * 提交审批。
     *
     * @param id 申请 ID
     * @param actor 当前操作人
     * @return 视图
     */
    InvoiceView submit(Long id, CrmActor actor);

    /**
     * 登记开票记录。
     *
     * @param id 申请 ID
     * @param command 开票命令
     * @param actor 当前操作人
     * @return 视图
     */
    InvoiceView issue(Long id, InvoiceIssueCommand command, CrmActor actor);

    /**
     * 登记寄送。
     *
     * @param id 申请 ID
     * @param command 寄送命令
     * @param actor 当前操作人
     * @return 视图
     */
    InvoiceView send(Long id, InvoiceSendCommand command, CrmActor actor);

    /**
     * 发票红冲。
     *
     * @param id 原申请 ID
     * @param command 红冲命令
     * @param actor 当前操作人
     * @return 红冲单据视图
     */
    InvoiceView redFlush(Long id, InvoiceRedFlushCommand command, CrmActor actor);

    /**
     * 取消申请（仅草稿或已驳回）。
     *
     * @param id 申请 ID
     * @param actor 当前操作人
     * @return 视图
     */
    InvoiceView cancel(Long id, CrmActor actor);

    /**
     * 处理审批回调（幂等）。
     *
     * @param command 回调命令
     * @return 状态发生变更返回 true
     */
    boolean handleApprovalCallback(InvoiceApprovalCallbackCommand command);

    /**
     * 计算来源单据可开票余额。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源单据 ID
     * @return 可开票余额
     */
    java.math.BigDecimal availableAmount(String sourceType, Long sourceId);
}

package com.bluewhale.crm.commerce.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.commerce.application.command.QuoteApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.command.QuoteSaveCommand;
import com.bluewhale.crm.commerce.application.view.QuoteView;
import com.bluewhale.crm.commerce.domain.model.QuoteQuery;

/**
 * 报价应用服务（M2 增量）。
 *
 * @author BlueWhale
 */
public interface QuoteAppService {

    /**
     * 报价分页。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<QuoteView> page(QuoteQuery query, CrmActor actor);

    /**
     * 报价详情。
     *
     * @param id 报价 ID
     * @param actor 当前操作人
     * @return 报价视图
     */
    QuoteView detail(Long id, CrmActor actor);

    /**
     * 新建报价（草稿）。
     *
     * @param command 保存命令
     * @param actor 当前操作人
     * @return 报价视图
     */
    QuoteView create(QuoteSaveCommand command, CrmActor actor);

    /**
     * 编辑草稿报价（全量替换行）。
     *
     * @param id 报价 ID
     * @param command 保存命令
     * @param actor 当前操作人
     * @return 报价视图
     */
    QuoteView update(Long id, QuoteSaveCommand command, CrmActor actor);

    /**
     * 提交审批。
     *
     * @param id 报价 ID
     * @param actor 当前操作人
     * @return 报价视图
     */
    QuoteView submit(Long id, CrmActor actor);

    /**
     * 改价：基于已通过 / 已驳回报价生成新版本草稿。
     *
     * @param id 源报价 ID
     * @param actor 当前操作人
     * @return 新版本报价视图
     */
    QuoteView revise(Long id, CrmActor actor);

    /**
     * 处理审批回调（幂等）。
     *
     * @param command 回调命令
     * @return 本次是否推进了状态
     */
    boolean handleApprovalCallback(QuoteApprovalCallbackCommand command);
}

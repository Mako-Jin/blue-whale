package com.bluewhale.oa.administrative.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.administrative.application.command.AdministrativeCommands;
import com.bluewhale.oa.administrative.application.view.AdministrativeViews;
import com.bluewhale.oa.administrative.domain.model.AdministrativeQuery;

/**
 * 采购应用服务。
 *
 * @author BlueWhale
 */
public interface PurchaseAppService {

    /**
     * 分页查询采购申请。
     *
     * @param query 查询条件
     * @return 采购申请分页结果
     */
    PageResult<AdministrativeViews.PurchaseView> page(AdministrativeQuery query);

    /**
     * 查询采购申请详情。
     *
     * @param id 采购申请ID
     * @return 采购申请详情
     */
    AdministrativeViews.PurchaseView detail(Long id);

    /**
     * 新建采购申请，并登记审批实例。
     *
     * @param command 采购申请保存命令
     * @return 新建后的采购申请
     */
    AdministrativeViews.PurchaseView create(AdministrativeCommands.PurchaseSaveCommand command);

    /**
     * 修改草稿状态的采购申请。
     *
     * @param id      采购申请ID
     * @param command 采购申请保存命令
     * @return 修改后的采购申请
     */
    AdministrativeViews.PurchaseView update(Long id, AdministrativeCommands.PurchaseSaveCommand command);

    /**
     * 删除采购申请。
     *
     * @param id 采购申请ID
     */
    void delete(Long id);

    /**
     * 变更采购申请状态，审批通过时追加 Outbox 事件。
     *
     * @param id      采购申请ID
     * @param command 状态变更命令
     */
    void updateStatus(Long id, AdministrativeCommands.PurchaseStatusCommand command);

    /**
     * 手工重试推送单条采购申请至 ERP。
     *
     * @param id 采购申请ID
     * @return 重试推送后的采购申请
     */
    AdministrativeViews.PurchaseView retryPush(Long id);

    /**
     * 批量推送待推送的采购申请至 ERP。
     *
     * @return 本次推送成功的条数
     */
    int pushPending();

    /**
     * 同步外部审批结果，按结果驱动采购状态变更。
     *
     * @param purchaseId 采购申请ID
     * @param result     审批结果状态值
     */
    void syncApprovalResult(Long purchaseId, String result);
}

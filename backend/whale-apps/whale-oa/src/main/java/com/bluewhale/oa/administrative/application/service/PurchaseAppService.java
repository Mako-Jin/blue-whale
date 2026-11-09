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
     * @param query 分页与筛选条件
     * @return 采购申请分页结果
     */
    PageResult<AdministrativeViews.PurchaseView> page(AdministrativeQuery query);

    /**
     * 查询采购申请详情。
     *
     * @param id 采购申请 ID
     * @return 采购申请详情
     */
    AdministrativeViews.PurchaseView detail(Long id);

    /**
     * 创建采购申请。
     *
     * @param command 采购申请保存命令
     * @return 创建后的采购申请
     */
    AdministrativeViews.PurchaseView create(AdministrativeCommands.PurchaseSaveCommand command);

    /**
     * 更新采购申请。
     *
     * @param id 采购申请 ID
     * @param command 采购申请保存命令
     * @return 更新后的采购申请
     */
    AdministrativeViews.PurchaseView update(Long id, AdministrativeCommands.PurchaseSaveCommand command);

    /**
     * 删除采购申请。
     *
     * @param id 采购申请 ID
     */
    void delete(Long id);

    /**
     * 更新采购申请状态。
     *
     * @param id 采购申请 ID
     * @param command 状态变更命令
     */
    void updateStatus(Long id, AdministrativeCommands.PurchaseStatusCommand command);

    /**
     * 重试推送采购申请至 ERP。
     *
     * @param id 采购申请 ID
     * @return 推送后的采购申请
     */
    AdministrativeViews.PurchaseView retryPush(Long id);

    /**
     * 推送所有待同步的采购申请。
     *
     * @return 成功推送的数量
     */
    int pushPending();

    /**
     * 同步外部审批结果至采购申请。
     *
     * @param purchaseId 采购申请 ID
     * @param result 审批结果
     */
    void syncApprovalResult(Long purchaseId, String result);
}

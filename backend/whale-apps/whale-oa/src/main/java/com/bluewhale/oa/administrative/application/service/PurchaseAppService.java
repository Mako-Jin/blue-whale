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

    PageResult<AdministrativeViews.PurchaseView> page(AdministrativeQuery query);

    AdministrativeViews.PurchaseView detail(Long id);

    AdministrativeViews.PurchaseView create(AdministrativeCommands.PurchaseSaveCommand command);

    AdministrativeViews.PurchaseView update(Long id, AdministrativeCommands.PurchaseSaveCommand command);

    void delete(Long id);

    void updateStatus(Long id, AdministrativeCommands.PurchaseStatusCommand command);

    AdministrativeViews.PurchaseView retryPush(Long id);

    int pushPending();

    void syncApprovalResult(Long purchaseId, String result);
}

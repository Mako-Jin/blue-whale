package com.bluewhale.oa.administrative.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.administrative.application.command.AdministrativeCommands;
import com.bluewhale.oa.administrative.application.view.AdministrativeViews;
import com.bluewhale.oa.administrative.domain.gateway.ContractSealGateway;
import com.bluewhale.oa.administrative.domain.model.AdministrativeQuery;

import java.util.List;

/**
 * 用章应用服务。
 *
 * @author BlueWhale
 */
public interface SealUseAppService {

    PageResult<AdministrativeViews.SealUseView> page(AdministrativeQuery query);

    AdministrativeViews.SealUseView detail(Long id);

    AdministrativeViews.SealUseView create(AdministrativeCommands.SealUseSaveCommand command);

    AdministrativeViews.SealUseView update(Long id, AdministrativeCommands.SealUseSaveCommand command);

    void delete(Long id);

    void updateStatus(Long id, AdministrativeCommands.SealUseStatusCommand command);

    AdministrativeViews.SealUseView register(Long id, Long operatorId);

    void syncApprovalResult(Long sealUseId, String result);

    List<ContractSealGateway.SealInfo> availableSeals(Long userId);
}

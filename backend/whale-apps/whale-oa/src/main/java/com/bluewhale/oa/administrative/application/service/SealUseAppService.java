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

    /**
     * 用章申请分页查询。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AdministrativeViews.SealUseView> page(AdministrativeQuery query);

    /**
     * 查询用章申请详情。
     *
     * @param id 用章申请ID
     * @return 用章申请视图
     */
    AdministrativeViews.SealUseView detail(Long id);

    /**
     * 新增用章申请。
     *
     * @param command 保存命令
     * @return 用章申请视图
     */
    AdministrativeViews.SealUseView create(AdministrativeCommands.SealUseSaveCommand command);

    /**
     * 编辑用章申请。
     *
     * @param id 用章申请ID
     * @param command 保存命令
     * @return 用章申请视图
     */
    AdministrativeViews.SealUseView update(Long id, AdministrativeCommands.SealUseSaveCommand command);

    /**
     * 删除用章申请。
     *
     * @param id 用章申请ID
     */
    void delete(Long id);

    /**
     * 变更用章申请状态。
     *
     * @param id 用章申请ID
     * @param command 状态变更命令
     */
    void updateStatus(Long id, AdministrativeCommands.SealUseStatusCommand command);

    /**
     * 登记用章。
     *
     * @param id 用章申请ID
     * @param operatorId 登记人用户ID
     * @return 用章申请视图
     */
    AdministrativeViews.SealUseView register(Long id, Long operatorId);

    /**
     * 同步审批结果到用章申请。
     *
     * @param sealUseId 用章申请ID
     * @param result 审批结果状态
     */
    void syncApprovalResult(Long sealUseId, String result);

    /**
     * 查询用户可用的印章列表。
     *
     * @param userId 用户ID
     * @return 已授权印章列表
     */
    List<ContractSealGateway.SealInfo> availableSeals(Long userId);
}

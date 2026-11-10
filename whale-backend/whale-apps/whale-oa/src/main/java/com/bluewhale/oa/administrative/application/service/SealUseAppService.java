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
     * 分页查询用章申请。
     *
     * @param query 分页与筛选条件
     * @return 用章申请分页结果
     */
    PageResult<AdministrativeViews.SealUseView> page(AdministrativeQuery query);

    /**
     * 查询用章申请详情。
     *
     * @param id 用章申请 ID
     * @return 用章申请详情
     */
    AdministrativeViews.SealUseView detail(Long id);

    /**
     * 创建用章申请。
     *
     * @param command 用章申请保存命令
     * @return 创建后的用章申请
     */
    AdministrativeViews.SealUseView create(AdministrativeCommands.SealUseSaveCommand command);

    /**
     * 更新用章申请。
     *
     * @param id 用章申请 ID
     * @param command 用章申请保存命令
     * @return 更新后的用章申请
     */
    AdministrativeViews.SealUseView update(Long id, AdministrativeCommands.SealUseSaveCommand command);

    /**
     * 删除用章申请。
     *
     * @param id 用章申请 ID
     */
    void delete(Long id);

    /**
     * 更新用章申请状态。
     *
     * @param id 用章申请 ID
     * @param command 状态变更命令
     */
    void updateStatus(Long id, AdministrativeCommands.SealUseStatusCommand command);

    /**
     * 登记用章使用记录。
     *
     * @param id 用章申请 ID
     * @param operatorId 操作人 ID
     * @return 登记后的用章申请
     */
    AdministrativeViews.SealUseView register(Long id, Long operatorId);

    /**
     * 同步外部审批结果至用章申请。
     *
     * @param sealUseId 用章申请 ID
     * @param result 审批结果
     */
    void syncApprovalResult(Long sealUseId, String result);

    /**
     * 查询用户可用的印章列表。
     *
     * @param userId 用户 ID
     * @return 可用印章列表
     */
    List<ContractSealGateway.SealInfo> availableSeals(Long userId);
}

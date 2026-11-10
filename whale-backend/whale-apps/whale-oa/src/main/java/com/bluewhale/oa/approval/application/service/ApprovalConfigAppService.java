package com.bluewhale.oa.approval.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalConfigSaveCommand;
import com.bluewhale.oa.approval.application.view.ApprovalConfigView;
import com.bluewhale.oa.approval.domain.model.ApprovalConfigQuery;

/**
 * 审批业务配置应用服务。
 *
 * @author BlueWhale
 */
public interface ApprovalConfigAppService {

    /**
     * 分页查询配置。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ApprovalConfigView> page(ApprovalConfigQuery query);

    /**
     * 查询配置详情。
     *
     * @param id 主键
     * @return 配置视图
     */
    ApprovalConfigView detail(Long id);

    /**
     * 新增配置。
     *
     * @param command 保存命令
     * @return 配置视图
     */
    ApprovalConfigView create(ApprovalConfigSaveCommand command);

    /**
     * 更新配置。
     *
     * @param id 主键
     * @param command 保存命令
     * @return 配置视图
     */
    ApprovalConfigView update(Long id, ApprovalConfigSaveCommand command);

    /**
     * 启用 / 停用配置。
     *
     * @param id 主键
     * @param enabled 目标状态
     * @return 配置视图
     */
    ApprovalConfigView changeEnabled(Long id, boolean enabled);

    /**
     * 获取已启用的业务配置（供审批发起链路内部使用）。
     *
     * @param businessType 业务类型
     * @return 配置视图
     */
    ApprovalConfigView requireEnabled(String businessType);
}

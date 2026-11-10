package com.bluewhale.oa.approval.application.service;

import com.bluewhale.oa.approval.application.command.ApprovalRouteRuleSaveCommand;
import com.bluewhale.oa.approval.application.view.ApprovalRouteRuleView;

import java.util.List;

/**
 * 审批人解析规则应用服务。
 *
 * @author BlueWhale
 */
public interface ApprovalRouteRuleAppService {

    /**
     * 查询业务类型下的规则。
     *
     * @param businessType 业务类型
     * @return 规则列表
     */
    List<ApprovalRouteRuleView> list(String businessType);

    /**
     * 保存规则（业务类型 + 节点唯一，存在则更新）。
     *
     * @param command 保存命令
     * @return 规则视图
     */
    ApprovalRouteRuleView save(ApprovalRouteRuleSaveCommand command);

    /**
     * 删除规则。
     *
     * @param id 主键
     */
    void delete(Long id);
}

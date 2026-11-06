package com.bluewhale.oa.approval.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalDelegateRuleCommand;
import com.bluewhale.oa.approval.application.view.ApprovalDelegateRuleView;
import com.bluewhale.oa.approval.domain.model.ApprovalActor;

/**
 * 审批代理设置应用服务。
 *
 * @author BlueWhale
 */
public interface ApprovalDelegateAppService {

    /**
     * 分页查询本人代理规则。
     *
     * @param actor 当前用户
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<ApprovalDelegateRuleView> page(ApprovalActor actor, long current, long size);

    /**
     * 新建代理规则。
     *
     * @param command 命令
     * @param actor 当前用户
     * @return 规则
     */
    ApprovalDelegateRuleView create(ApprovalDelegateRuleCommand command, ApprovalActor actor);

    /**
     * 修改本人代理规则。
     *
     * @param id 规则ID
     * @param command 命令
     * @param actor 当前用户
     * @return 规则
     */
    ApprovalDelegateRuleView update(Long id, ApprovalDelegateRuleCommand command, ApprovalActor actor);

    /**
     * 删除本人代理规则。
     *
     * @param id 规则ID
     * @param actor 当前用户
     */
    void delete(Long id, ApprovalActor actor);
}

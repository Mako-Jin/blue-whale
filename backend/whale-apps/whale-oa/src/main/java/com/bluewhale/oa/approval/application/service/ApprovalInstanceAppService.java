package com.bluewhale.oa.approval.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalInstanceRegisterCommand;
import com.bluewhale.oa.approval.application.command.ApprovalResultCommand;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.approval.domain.model.ApprovalInstanceQuery;

/**
 * 审批实例应用服务。
 *
 * @author BlueWhale
 */
public interface ApprovalInstanceAppService {

    /**
     * 分页查询审批实例。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ApprovalInstanceView> page(ApprovalInstanceQuery query);

    /**
     * 查询审批实例详情（含审批人快照与业务副作用）。
     *
     * @param id 主键
     * @return 审批实例视图
     */
    ApprovalInstanceView detail(Long id);

    /**
     * 按业务单据查询审批实例。
     *
     * @param businessType 业务类型
     * @param businessId 业务单据ID
     * @return 审批实例视图
     */
    ApprovalInstanceView findByBusiness(String businessType, Long businessId);

    /**
     * 判断用户是否属于该业务单据审批的参与者。
     *
     * <p>判定口径：审批发起人、审批人快照命中、Workflow 流程实例的发起人 / 任务处理人
     * （含所属人、委派来源）。业务上下文（如公文附件下载）据此决定未办结单据的可读范围。</p>
     *
     * @param businessType 业务类型
     * @param businessId 业务单据ID
     * @param userId 用户ID，可为空
     * @param username 用户名，可为空
     * @return 属于参与者返回 {@code true}；未登记审批实例或工作流不可用时返回 {@code false}
     */
    boolean isParticipant(String businessType, Long businessId, Long userId, String username);

    /**
     * 登记审批实例（同一业务单据幂等，重复登记返回既有实例）。
     *
     * @param command 登记命令
     * @return 审批实例视图
     */
    ApprovalInstanceView register(ApprovalInstanceRegisterCommand command);

    /**
     * 绑定 Workflow 流程实例。
     *
     * @param id 审批实例ID
     * @param workflowInstanceId Workflow 流程实例ID
     * @return 审批实例视图
     */
    ApprovalInstanceView attachWorkflow(Long id, Long workflowInstanceId);

    /**
     * 同步审批结果（幂等；重复同步同一终态不重复执行业务副作用）。
     *
     * @param id 审批实例ID
     * @param command 结果命令
     * @return 审批实例视图
     */
    ApprovalInstanceView syncResult(Long id, ApprovalResultCommand command);
}

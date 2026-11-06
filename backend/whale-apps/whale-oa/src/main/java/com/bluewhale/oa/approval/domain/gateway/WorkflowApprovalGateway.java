package com.bluewhale.oa.approval.domain.gateway;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.domain.model.WorkflowCcQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowCcSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowDelegateCommand;
import com.bluewhale.oa.approval.domain.model.WorkflowDelegateRuleSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowTaskQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowTaskSnapshot;

import java.util.List;
import java.util.Map;

/**
 * Workflow 审批内网门面端口。
 *
 * <p>OA 审批中心只通过本端口访问 Workflow，不直接暴露 {@code sys:workflow:*} 给员工。</p>
 *
 * @author BlueWhale
 */
public interface WorkflowApprovalGateway {

    /**
     * 分页查询任务。
     *
     * @param query 查询条件
     * @return 任务分页
     */
    PageResult<WorkflowTaskSnapshot> pageTasks(WorkflowTaskQuery query);

    /**
     * 分页查询流程实例。
     *
     * @param query 查询条件
     * @return 实例分页
     */
    PageResult<WorkflowInstanceSnapshot> pageInstances(WorkflowInstanceQuery query);

    /**
     * 查询流程实例详情（含任务轨迹）。
     *
     * @param instanceId 流程实例ID
     * @return 实例详情
     */
    WorkflowInstanceSnapshot getInstance(Long instanceId);

    /**
     * 分页查询抄送记录。
     *
     * @param query 查询条件
     * @return 抄送分页
     */
    PageResult<WorkflowCcSnapshot> pageCcs(WorkflowCcQuery query);

    /**
     * 按主键查询抄送记录。
     *
     * @param ccId 抄送记录ID
     * @return 抄送记录
     */
    WorkflowCcSnapshot getCc(Long ccId);

    /**
     * 标记抄送已读（幂等）。
     *
     * @param ccId 抄送记录ID
     */
    void markCcRead(Long ccId);

    /**
     * 完成人工审批任务（同意/驳回，幂等）。
     *
     * @param taskId 任务ID
     * @param action APPROVE / REJECT
     * @param comment 审批意见
     * @param variables 追加流程变量
     * @param operatorId 操作人用户ID
     * @param operatorAccount 操作人账号
     */
    void completeTask(Long taskId, String action, String comment, Map<String, Object> variables,
                      String operatorId, String operatorAccount);

    /**
     * 转办任务。
     *
     * @param taskId 任务ID
     * @param targetUser 目标账号
     */
    void transferTask(Long taskId, String targetUser);

    /**
     * 加签。
     *
     * @param taskId 任务ID
     * @param assignees 加签账号
     */
    void addSign(Long taskId, List<String> assignees);

    /**
     * 回退到指定节点。
     *
     * @param taskId 任务ID
     * @param targetNodeKey 目标节点键
     */
    void rollbackTask(Long taskId, String targetNodeKey);

    /**
     * 催办实例。
     *
     * @param instanceId 流程实例ID
     * @param urger 催办人账号
     * @param targetAccount 被催办人账号
     * @param comment 催办说明
     */
    void urge(Long instanceId, String urger, String targetAccount, String comment);

    /**
     * 撤销运行中的实例。
     *
     * @param instanceId 流程实例ID
     */
    void cancelInstance(Long instanceId);

    /**
     * 分页查询代理规则。
     *
     * @param fromAccount 委托人账号
     * @param current 页码
     * @param size 每页条数
     * @return 代理规则分页
     */
    PageResult<WorkflowDelegateRuleSnapshot> pageDelegateRules(String fromAccount, long current, long size);

    /**
     * 新建代理规则。
     *
     * @param command 保存命令
     * @return 新建后的规则
     */
    WorkflowDelegateRuleSnapshot createDelegateRule(WorkflowDelegateCommand command);

    /**
     * 更新代理规则。
     *
     * @param id 规则ID
     * @param command 保存命令
     * @return 更新后的规则
     */
    WorkflowDelegateRuleSnapshot updateDelegateRule(Long id, WorkflowDelegateCommand command);

    /**
     * 删除代理规则。
     *
     * @param id 规则ID
     */
    void deleteDelegateRule(Long id);
}

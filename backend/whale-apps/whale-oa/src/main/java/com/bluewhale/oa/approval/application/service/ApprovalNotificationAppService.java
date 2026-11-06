package com.bluewhale.oa.approval.application.service;

/**
 * 审批通知应用服务。
 *
 * <p>所有方法均为尽力而为：消息服务不可用或账号解析失败时只记录日志，不影响审批主流程。</p>
 *
 * @author BlueWhale
 */
public interface ApprovalNotificationAppService {

    /**
     * 发送待办通知给当前审批人。
     *
     * @param workflowInstanceId 流程实例ID
     */
    void notifyTodo(Long workflowInstanceId);

    /**
     * 发送审批结果通知给发起人。
     *
     * @param businessType 业务类型
     * @param businessId 业务单据ID
     * @param status 结果状态
     */
    void notifyResult(String businessType, Long businessId, String status);

    /**
     * 发送催办通知。
     *
     * @param workflowInstanceId 流程实例ID
     * @param targetAccount 被催办人账号；为空时通知全部当前待办人
     * @param comment 催办说明
     * @param urger 催办人账号
     */
    void notifyUrge(Long workflowInstanceId, String targetAccount, String comment, String urger);

    /**
     * 发送抄送通知给未读抄送人。
     *
     * @param workflowInstanceId 流程实例ID
     */
    void notifyCc(Long workflowInstanceId);

    /**
     * 发送自动催办通知（按天幂等）。
     *
     * @param workflowInstanceId 流程实例ID
     */
    void notifyAutoUrge(Long workflowInstanceId);
}

package com.bluewhale.oa.approval.application.event;

/**
 * 审批实例绑定 Workflow 事件。
 *
 * <p>首次绑定成功后发布，用于向当前审批人发送待办通知。</p>
 *
 * @param approvalInstanceId 本地审批实例ID
 * @param workflowInstanceId Workflow 流程实例ID
 * @author BlueWhale
 */
public record ApprovalWorkflowAttachedEvent(Long approvalInstanceId, Long workflowInstanceId) {
}

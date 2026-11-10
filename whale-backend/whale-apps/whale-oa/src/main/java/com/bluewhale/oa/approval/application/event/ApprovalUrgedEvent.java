package com.bluewhale.oa.approval.application.event;

/**
 * 审批催办事件。
 *
 * <p>催办接口调用成功后发布，由通知服务补发站内信。</p>
 *
 * @param workflowInstanceId 流程实例ID
 * @param targetAccount 被催办人账号，可为空
 * @param comment 催办说明
 * @param urger 催办人账号
 * @author BlueWhale
 */
public record ApprovalUrgedEvent(Long workflowInstanceId, String targetAccount, String comment, String urger) {
}

package com.bluewhale.oa.approval.application.event;

/**
 * 审批结果同步事件。
 *
 * <p>审批实例同步终态后发布，由业务方（如请假）监听并回写业务单据状态；
 * 事件只携带业务标识与结果，避免审批上下文反向依赖业务上下文。</p>
 *
 * @param businessType 业务类型，如 LEAVE
 * @param businessId 业务单据ID
 * @param result 审批结果：APPROVED / REJECTED / CANCELLED
 * @author BlueWhale
 */
public record ApprovalResultSyncedEvent(String businessType, Long businessId, String result) {
}

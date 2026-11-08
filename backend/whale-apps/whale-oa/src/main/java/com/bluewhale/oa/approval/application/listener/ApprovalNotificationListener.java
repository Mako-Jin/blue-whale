package com.bluewhale.oa.approval.application.listener;

import com.bluewhale.oa.approval.application.event.ApprovalResultSyncedEvent;
import com.bluewhale.oa.approval.application.event.ApprovalUrgedEvent;
import com.bluewhale.oa.approval.application.event.ApprovalWorkflowAttachedEvent;
import com.bluewhale.oa.approval.application.service.ApprovalNotificationAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 审批通知监听器。
 *
 * <p>把审批领域事件转换为站内通知；通知服务内部已做异常兜底，监听器不阻塞事件发布方。</p>
 *
 * <p>监听改为 {@code @Async + @TransactionalEventListener(AFTER_COMMIT)}：通知发送不再阻塞审批主链路，
 * 且在事务提交后才执行（事务内发布的事件发布方尚未提交时，同步监听会读不到未提交数据）；
 * 非事务上下文发布的事件（如催办）通过 {@code fallbackExecution} 兼容。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ApprovalNotificationListener {

    private final ApprovalNotificationAppService approvalNotificationAppService;

    /**
     * 实例绑定后发送待办与抄送通知。
     *
     * @param event 绑定事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkflowAttached(ApprovalWorkflowAttachedEvent event) {
        approvalNotificationAppService.notifyTodo(event.workflowInstanceId());
        approvalNotificationAppService.notifyCc(event.workflowInstanceId());
    }

    /**
     * 结果同步后通知发起人。
     *
     * @param event 结果同步事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onResultSynced(ApprovalResultSyncedEvent event) {
        approvalNotificationAppService.notifyResult(event.businessType(), event.businessId(), event.result());
    }

    /**
     * 催办后通知被催办人。
     *
     * @param event 催办事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUrged(ApprovalUrgedEvent event) {
        approvalNotificationAppService.notifyUrge(event.workflowInstanceId(), event.targetAccount(),
                event.comment(), event.urger());
    }
}

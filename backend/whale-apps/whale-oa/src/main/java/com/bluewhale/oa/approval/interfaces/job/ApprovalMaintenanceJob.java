package com.bluewhale.oa.approval.interfaces.job;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.oa.approval.application.command.ApprovalResultCommand;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.service.ApprovalNotificationAppService;
import com.bluewhale.oa.approval.domain.gateway.WorkflowApprovalGateway;
import com.bluewhale.oa.approval.domain.model.ApprovalInstance;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceStatusSnapshot;
import com.bluewhale.oa.approval.domain.repository.ApprovalInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审批维护任务：自动催办与状态对账。
 *
 * <p>两项任务均按批处理并逐条兜底，单条失败不影响后续记录。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalMaintenanceJob {

    /** 单次对账批量上限（Workflow 内网接口单次最多 100 个实例） */
    private static final int RECONCILE_CHUNK_SIZE = 100;

    /** Workflow 终态：已通过 */
    private static final int WORKFLOW_APPROVED = 1;

    /** Workflow 终态：已驳回 */
    private static final int WORKFLOW_REJECTED = 2;

    /** Workflow 终态：已撤销 */
    private static final int WORKFLOW_CANCELLED = 3;

    private final ApprovalInstanceRepository approvalInstanceRepository;

    private final ApprovalInstanceAppService approvalInstanceAppService;

    private final ApprovalNotificationAppService approvalNotificationAppService;

    private final WorkflowApprovalGateway workflowApprovalGateway;

    /** 自动催办开关 */
    @Value("${whale.oa.approval.auto-urge.enabled:true}")
    private boolean autoUrgeEnabled;

    /** 自动催办阈值（小时） */
    @Value("${whale.oa.approval.auto-urge.threshold-hours:24}")
    private long autoUrgeThresholdHours;

    /** 自动催办单批条数 */
    @Value("${whale.oa.approval.auto-urge.batch-size:100}")
    private int autoUrgeBatchSize;

    /** 对账开关 */
    @Value("${whale.oa.approval.reconcile.enabled:true}")
    private boolean reconcileEnabled;

    /** 对账单批条数 */
    @Value("${whale.oa.approval.reconcile.batch-size:200}")
    private int reconcileBatchSize;

    /**
     * 自动催办：为超阈值未处理的审批发送提醒（消息服务按天去重）。
     *
     * @return 执行摘要（{@code skipped} 或扫描/提醒条数）
     */
    @ScheduledJob(code = "oa.approval.auto-urge",
            cron = "${whale.oa.approval.auto-urge.cron:0 0 */2 * * *}",
            timeoutSeconds = 1800)
    public String autoUrge() {
        if (!autoUrgeEnabled) {
            return "skipped: auto-urge disabled";
        }
        LocalDateTime threshold = LocalDateTime.now().minusHours(Math.max(autoUrgeThresholdHours, 1L));
        List<ApprovalInstance> instances = approvalInstanceRepository
                .findRunningSubmittedBefore(threshold, Math.max(autoUrgeBatchSize, 1));
        int notified = 0;
        for (ApprovalInstance instance : instances) {
            try {
                approvalNotificationAppService.notifyAutoUrge(instance.getWorkflowInstanceId());
                notified++;
            } catch (RuntimeException exception) {
                log.warn("自动催办失败: approvalInstanceId={}, error={}",
                        instance.getId(), exception.getMessage());
            }
        }
        if (notified > 0) {
            log.info("自动催办完成: total={}, notified={}", instances.size(), notified);
        }
        return "total=" + instances.size() + ", notified=" + notified;
    }

    /**
     * 状态对账：把 Workflow 终态回写本地审批实例并触发业务结果同步。
     *
     * @return 执行摘要（{@code skipped} 或扫描/回写条数）
     */
    @ScheduledJob(code = "oa.approval.reconcile",
            cron = "${whale.oa.approval.reconcile.cron:0 */30 * * * *}",
            timeoutSeconds = 1800)
    public String reconcile() {
        if (!reconcileEnabled) {
            return "skipped: reconcile disabled";
        }
        List<ApprovalInstance> instances = approvalInstanceRepository
                .findRunningWithWorkflow(Math.max(reconcileBatchSize, 1));
        int synced = 0;
        for (int start = 0; start < instances.size(); start += RECONCILE_CHUNK_SIZE) {
            int end = Math.min(start + RECONCILE_CHUNK_SIZE, instances.size());
            List<ApprovalInstance> chunk = instances.subList(start, end);
            synced += reconcileChunk(chunk);
        }
        if (synced > 0) {
            log.info("审批状态对账完成: scanned={}, synced={}", instances.size(), synced);
        }
        return "scanned=" + instances.size() + ", synced=" + synced;
    }

    /**
     * 对账单批实例。
     *
     * @param chunk 实例批次
     * @return 成功回写条数
     */
    private int reconcileChunk(List<ApprovalInstance> chunk) {
        List<Long> workflowIds = chunk.stream().map(ApprovalInstance::getWorkflowInstanceId).distinct().toList();
        List<WorkflowInstanceStatusSnapshot> statuses;
        try {
            statuses = workflowApprovalGateway.listInstanceStatuses(workflowIds);
        } catch (RuntimeException exception) {
            log.warn("批量查询流程状态失败: size={}, error={}", workflowIds.size(), exception.getMessage());
            return 0;
        }
        Map<Long, Integer> statusMap = statuses.stream()
                .filter(status -> status.getId() != null && status.getStatus() != null)
                .collect(Collectors.toMap(WorkflowInstanceStatusSnapshot::getId,
                        WorkflowInstanceStatusSnapshot::getStatus, (left, right) -> right));
        int synced = 0;
        for (ApprovalInstance instance : chunk) {
            String target = mapStatus(statusMap.get(instance.getWorkflowInstanceId()));
            if (target == null) {
                continue;
            }
            ApprovalResultCommand command = new ApprovalResultCommand();
            command.setStatus(target);
            try {
                approvalInstanceAppService.syncResult(instance.getId(), command);
                synced++;
            } catch (RuntimeException exception) {
                log.warn("审批状态回写失败: approvalInstanceId={}, status={}, error={}",
                        instance.getId(), target, exception.getMessage());
            }
        }
        return synced;
    }

    /**
     * Workflow 状态数字转审批结果枚举。
     *
     * @param status Workflow 状态
     * @return 审批结果；非终态返回 {@code null}
     */
    private String mapStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case WORKFLOW_APPROVED -> "APPROVED";
            case WORKFLOW_REJECTED -> "REJECTED";
            case WORKFLOW_CANCELLED -> "CANCELLED";
            default -> null;
        };
    }
}

package com.bluewhale.workflow.scheduler;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.workflow.config.WorkflowTimeoutProperties;
import com.bluewhale.workflow.mapper.OverdueTaskRow;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.WorkflowMessageClient;
import com.bluewhale.workflow.support.WorkflowMetrics;
import com.bluewhale.workflow.support.WorkflowUserDirectoryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 待办超时扫描任务。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>只扫描「待办且 {@code due_at <= now}」的任务，<b>不自动通过/驳回</b>，避免业务风险；</li>
 *   <li>命中条数写入指标 {@code workflow.task.overdue}，供告警与看板使用；</li>
 *   <li>提醒默认关闭（{@code bluewhale.workflow.timeout.notify-enabled}），开启后按「任务 + 小时」
 *       幂等键投递站内信，同一任务每小时最多一条；解析不到用户ID/投递失败只记录指标与日志。</li>
 * </ul>
 *
 * <p>后台线程无租户上下文：查询跨租户，提醒前按行内 {@code tenant_id} 绑定
 * {@link TenantContext}，调用结束立即清理，避免串租户。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTimeoutScheduler {

    /** 幂等键时间窗（小时精度） */
    private static final DateTimeFormatter HOUR_BUCKET = DateTimeFormatter.ofPattern("yyyyMMddHH");

    /** 提醒标题 */
    private static final String REMINDER_TITLE = "审批待办已超时";

    private final ProcessTaskMapper processTaskMapper;

    private final WorkflowMetrics workflowMetrics;

    private final WorkflowTimeoutProperties properties;

    private final WorkflowUserDirectoryClient userDirectoryClient;

    private final WorkflowMessageClient messageClient;

    /**
     * 扫描一轮超时待办并按配置发送提醒。
     *
     * @return 执行摘要（命中数 / 已发送 / 跳过 / 失败）
     */
    @ScheduledJob(code = "workflow.task.overdue",
            cron = "${bluewhale.workflow.timeout.scan-cron:0 */10 * * * *}",
            timeoutSeconds = 120)
    public String scan() {
        LocalDateTime now = LocalDateTime.now();
        List<OverdueTaskRow> overdue = processTaskMapper.selectOverdue(now, properties.getScanLimit());
        workflowMetrics.recordTaskOverdue(overdue.size());
        if (overdue.isEmpty()) {
            return "overdue=0";
        }
        log.warn("待办超时扫描命中：overdue={}, scanLimit={}", overdue.size(), properties.getScanLimit());
        if (!properties.isNotifyEnabled()) {
            return "overdue=" + overdue.size() + ", notify=disabled";
        }
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        String bucket = now.format(HOUR_BUCKET);
        for (OverdueTaskRow row : overdue) {
            ReminderResult result = remind(row, bucket);
            if (result == ReminderResult.SENT) {
                sent++;
            } else if (result == ReminderResult.FAILED) {
                failed++;
            } else {
                skipped++;
            }
        }
        return "overdue=" + overdue.size() + ", sent=" + sent + ", skipped=" + skipped + ", failed=" + failed;
    }

    /**
     * 为单条超时待办投递提醒。
     *
     * @param row 超时待办
     * @param bucket 幂等时间窗
     * @return 投递结果
     */
    private ReminderResult remind(OverdueTaskRow row, String bucket) {
        if (row.getTenantId() == null || !StringUtils.hasText(row.getAssignee())) {
            workflowMetrics.recordOverdueReminder(WorkflowMetrics.REMINDER_SKIPPED);
            return ReminderResult.SKIPPED;
        }
        TenantContext.set(row.getTenantId());
        try {
            Long userId = userDirectoryClient.resolve(row.getAssignee(), row.getTenantId());
            if (userId == null) {
                workflowMetrics.recordOverdueReminder(WorkflowMetrics.REMINDER_SKIPPED);
                return ReminderResult.SKIPPED;
            }
            String key = "workflow-overdue:" + row.getId() + ":" + bucket;
            boolean ok = messageClient.sendTimeoutReminder(row.getTenantId(), key,
                    REMINDER_TITLE, contentOf(row), userId);
            workflowMetrics.recordOverdueReminder(ok
                    ? WorkflowMetrics.REMINDER_SENT : WorkflowMetrics.REMINDER_FAILED);
            return ok ? ReminderResult.SENT : ReminderResult.FAILED;
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 构造提醒正文。
     *
     * @param row 超时待办
     * @return 正文
     */
    private static String contentOf(OverdueTaskRow row) {
        String nodeName = StringUtils.hasText(row.getNodeName()) ? row.getNodeName() : "审批节点";
        return "您有一条「" + nodeName + "」待办已超过截止时间，请尽快处理。"
                + "（流程实例 " + row.getInstanceId() + "，待办 " + row.getId() + "）";
    }

    /**
     * 单条提醒的投递结果。
     */
    private enum ReminderResult {

        /** 已投递 */
        SENT,

        /** 跳过（账号解析不到或数据不完整） */
        SKIPPED,

        /** 投递失败 */
        FAILED
    }
}

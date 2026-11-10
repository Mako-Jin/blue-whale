package com.bluewhale.erp.analytics.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 报表定时计划聚合。
 *
 * <p>不变量：计划编号租户内唯一；Cron 表达式与推送渠道必填；外部推送未启用时仅记录执行时间。</p>
 *
 * @author BlueWhale
 */
@Data
public class ReportSchedule {

    /** 允许的推送渠道 */
    private static final Set<String> CHANNELS = Set.of("INTERNAL", "EMAIL", "MESSAGE");

    /** 主键 */
    private Long id;

    /** 计划编号 */
    private String scheduleNo;

    /** 报表编号 */
    private String reportNo;

    /** 计划名称 */
    private String scheduleName;

    /** Cron 表达式 */
    private String cronExpression;

    /** 推送渠道 */
    private String channel;

    /** 接收人 */
    private String recipients;

    /** 上次执行时间 */
    private LocalDateTime lastRunTime;

    /** 下次执行时间 */
    private LocalDateTime nextRunTime;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 校验计划数据。
     */
    public void validateForCreate() {
        if (scheduleNo == null || scheduleNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "计划编号不能为空");
        }
        if (scheduleName == null || scheduleName.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "计划名称不能为空");
        }
        if (reportNo == null || reportNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "报表编号不能为空");
        }
        if (cronExpression == null || cronExpression.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "Cron 表达式不能为空");
        }
        channel = channel == null || channel.isBlank() ? "INTERNAL" : channel;
        if (!CHANNELS.contains(channel)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID,
                    "推送渠道仅支持 INTERNAL / EMAIL / MESSAGE");
        }
        status = status == null || status.isBlank() ? AnalyticsStatus.ENABLED : status;
    }

    /**
     * 标记本次执行，并按日顺延下次执行时间（精确 Cron 调度由调度器接管）。
     *
     * @param now 当前时间
     */
    public void markRun(LocalDateTime now) {
        if (AnalyticsStatus.DISABLED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ALERT_STATE_CONFLICT, "计划已停用，不能执行");
        }
        lastRunTime = now;
        nextRunTime = now.plusDays(1);
    }
}

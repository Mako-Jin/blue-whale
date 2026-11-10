package com.bluewhale.erp.analytics.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表定时计划视图。
 *
 * @author BlueWhale
 */
@Data
public class ReportScheduleView {

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
}

package com.bluewhale.erp.analytics.application.command;

import lombok.Data;

/**
 * 报表定时计划保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ReportScheduleSaveCommand {

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

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}

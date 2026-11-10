package com.bluewhale.erp.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 报表定时计划实体，对应表 {@code erp_bi_report_schedule}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_bi_report_schedule")
public class ReportScheduleEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

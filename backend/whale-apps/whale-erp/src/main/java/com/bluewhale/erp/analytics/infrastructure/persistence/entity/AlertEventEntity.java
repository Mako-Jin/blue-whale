package com.bluewhale.erp.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预警事件实体，对应表 {@code erp_bi_alert_event}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_bi_alert_event")
public class AlertEventEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 事件编号 */
    private String eventNo;

    /** 规则ID */
    private Long ruleId;

    /** 规则编号快照 */
    private String ruleNo;

    /** 规则类型快照 */
    private String ruleType;

    /** 预警对象键 */
    private String objectKey;

    /** 统计周期编码 */
    private String periodCode;

    /** 实际值 */
    private BigDecimal metricValue;

    /** 阈值 */
    private BigDecimal thresholdValue;

    /** 级别 */
    private String level;

    /** 状态 */
    private String status;

    /** 计划建议 */
    private String suggestion;

    /** 触发时间 */
    private LocalDateTime triggeredAt;

    /** 确认人 */
    private String acknowledgedBy;

    /** 确认时间 */
    private LocalDateTime acknowledgedAt;

    /** 关闭人 */
    private String closedBy;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 备注 */
    private String remark;
}

package com.bluewhale.erp.analytics.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预警事件聚合。
 *
 * <p>不变量：状态按 {@code OPEN → ACKNOWLEDGED → CLOSED} 单向推进；已关闭事件不可重复处理。</p>
 *
 * @author BlueWhale
 */
@Data
public class AlertEvent {

    /** 主键 */
    private Long id;

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

    /**
     * 校验事件数据。
     */
    public void validateForCreate() {
        if (objectKey == null || objectKey.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "预警对象不能为空");
        }
        if (periodCode == null || periodCode.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "预警周期不能为空");
        }
        metricValue = metricValue == null ? BigDecimal.ZERO : metricValue;
        thresholdValue = thresholdValue == null ? BigDecimal.ZERO : thresholdValue;
        triggeredAt = triggeredAt == null ? LocalDateTime.now() : triggeredAt;
        status = AnalyticsStatus.EVENT_OPEN;
    }

    /**
     * 确认预警事件。
     *
     * @param operator 操作人
     * @param now 操作时间
     */
    public void acknowledge(String operator, LocalDateTime now) {
        if (AnalyticsStatus.EVENT_CLOSED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ALERT_STATE_CONFLICT, "预警事件已关闭，不能确认");
        }
        acknowledgedBy = operator;
        acknowledgedAt = now;
        status = AnalyticsStatus.EVENT_ACKNOWLEDGED;
    }

    /**
     * 关闭预警事件。
     *
     * @param operator 操作人
     * @param now 操作时间
     */
    public void close(String operator, LocalDateTime now) {
        if (AnalyticsStatus.EVENT_CLOSED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.ALERT_STATE_CONFLICT, "预警事件已关闭");
        }
        closedBy = operator;
        closedAt = now;
        status = AnalyticsStatus.EVENT_CLOSED;
    }
}

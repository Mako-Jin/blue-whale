package com.bluewhale.erp.analytics.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 预警规则聚合。
 *
 * <p>不变量：规则编号租户内唯一；规则类型与统计周期必须合法；阈值非负；
 * 同规则同对象同周期不重复触发由事件唯一键保证。</p>
 *
 * @author BlueWhale
 */
@Data
public class AlertRule {

    /** 规则类型允许集合 */
    private static final Set<String> RULE_TYPES = Set.of(
            AnalyticsStatus.RULE_STOCK_LOW,
            AnalyticsStatus.RULE_RECEIVABLE_OVERDUE,
            AnalyticsStatus.RULE_QUALITY_OPEN_NCR,
            AnalyticsStatus.RULE_BUDGET_EXCEEDED);

    /** 周期允许集合 */
    private static final Set<String> PERIOD_TYPES = Set.of(
            AnalyticsStatus.PERIOD_DAY,
            AnalyticsStatus.PERIOD_WEEK,
            AnalyticsStatus.PERIOD_MONTH);

    /** 级别允许集合 */
    private static final Set<String> LEVELS = Set.of(
            AnalyticsStatus.LEVEL_INFO,
            AnalyticsStatus.LEVEL_WARN,
            AnalyticsStatus.LEVEL_CRITICAL);

    /** 主键 */
    private Long id;

    /** 规则编号 */
    private String ruleNo;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型 */
    private String ruleType;

    /** 阈值 */
    private BigDecimal thresholdValue;

    /** 统计周期 */
    private String periodType;

    /** 级别 */
    private String level;

    /** 计划建议模板 */
    private String suggestion;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 校验规则数据。
     */
    public void validateForCreate() {
        validateRequired();
        if (thresholdValue.signum() < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "预警阈值不能为负数");
        }
        normalizePeriod();
        normalizeLevel();
        status = status == null || status.isBlank() ? AnalyticsStatus.ENABLED : status;
    }

    /**
     * 校验必填与枚举值。
     */
    private void validateRequired() {
        if (ruleNo == null || ruleNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "规则编号不能为空");
        }
        if (ruleName == null || ruleName.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "规则名称不能为空");
        }
        if (!RULE_TYPES.contains(ruleType)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "预警类型不支持");
        }
        thresholdValue = thresholdValue == null ? BigDecimal.ZERO : thresholdValue;
    }

    /**
     * 规范化统计周期。
     */
    private void normalizePeriod() {
        periodType = periodType == null || periodType.isBlank()
                ? AnalyticsStatus.PERIOD_DAY : periodType;
        if (!PERIOD_TYPES.contains(periodType)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID,
                    "统计周期仅支持 DAY / WEEK / MONTH");
        }
    }

    /**
     * 规范化预警级别。
     */
    private void normalizeLevel() {
        level = level == null || level.isBlank() ? AnalyticsStatus.LEVEL_WARN : level;
        if (!LEVELS.contains(level)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID,
                    "预警级别仅支持 INFO / WARN / CRITICAL");
        }
    }
}

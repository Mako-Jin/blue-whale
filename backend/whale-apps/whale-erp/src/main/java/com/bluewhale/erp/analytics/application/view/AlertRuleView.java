package com.bluewhale.erp.analytics.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预警规则视图。
 *
 * @author BlueWhale
 */
@Data
public class AlertRuleView {

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
}

package com.bluewhale.erp.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预警规则实体，对应表 {@code erp_bi_alert_rule}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_bi_alert_rule")
public class AlertRuleEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

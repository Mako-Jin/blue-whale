package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行动计划规则。
 *
 * @author BlueWhale
 */
@Data
public class ActionRule {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型 */
    private ActionRuleType ruleType;

    /** 阈值天数 */
    private Integer thresholdDays;

    /** 是否启用 */
    private Boolean enabled;

    /** 规则说明 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 停用规则。
     */
    public void disable() {
        this.enabled = Boolean.FALSE;
    }
}

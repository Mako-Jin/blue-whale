package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户公海回收规则。
 *
 * @author BlueWhale
 */
@Data
public class CustomerPoolRule {

    /** 主键 */
    private Long id;

    /** 规则名称 */
    private String ruleName;

    /** 未跟进天数阈值（0 表示不限制） */
    private Integer inactiveDays;

    /** 无商机天数阈值（0 表示不限制） */
    private Integer noOpportunityDays;

    /** 适用等级（逗号分隔） */
    private String levels;

    /** 适用部门（NULL 表示全部） */
    private Long deptId;

    /** 每人每日领取上限（0 表示不限制） */
    private Integer dailyClaimLimit;

    /** 是否启用 */
    private Boolean enabled;

    /** 最近执行时间 */
    private LocalDateTime lastRunAt;

    /** 最近回收数量 */
    private Integer recycleCount;

    /**
     * 构建默认规则。
     *
     * @return 默认规则（30 天未跟进、C/D 级、不限领取）
     */
    public static CustomerPoolRule defaults() {
        CustomerPoolRule rule = new CustomerPoolRule();
        rule.setRuleName("默认公海规则");
        rule.setInactiveDays(30);
        rule.setNoOpportunityDays(0);
        rule.setLevels("C,D");
        rule.setDailyClaimLimit(0);
        rule.setEnabled(Boolean.TRUE);
        rule.setRecycleCount(0);
        return rule;
    }

    /**
     * 是否启用。
     *
     * @return 启用返回 true
     */
    public boolean active() {
        return Boolean.TRUE.equals(enabled);
    }
}

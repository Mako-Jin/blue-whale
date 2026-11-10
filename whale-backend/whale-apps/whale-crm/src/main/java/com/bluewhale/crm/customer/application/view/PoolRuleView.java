package com.bluewhale.crm.customer.application.view;

import com.bluewhale.crm.customer.domain.model.CustomerPoolRule;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公海规则视图。
 *
 * @author BlueWhale
 */
@Data
public class PoolRuleView {

    /** 主键 */
    private Long id;

    /** 规则名称 */
    private String ruleName;

    /** 未跟进天数 */
    private Integer inactiveDays;

    /** 无商机天数 */
    private Integer noOpportunityDays;

    /** 适用等级 */
    private String levels;

    /** 适用部门 */
    private Long deptId;

    /** 每日领取上限 */
    private Integer dailyClaimLimit;

    /** 是否启用 */
    private Boolean enabled;

    /** 最近执行时间 */
    private LocalDateTime lastRunAt;

    /** 最近回收数量 */
    private Integer recycleCount;

    /**
     * 由领域模型构建视图。
     *
     * @param rule 公海规则
     * @return 规则视图
     */
    public static PoolRuleView from(CustomerPoolRule rule) {
        PoolRuleView view = new PoolRuleView();
        view.setId(rule.getId());
        view.setRuleName(rule.getRuleName());
        view.setInactiveDays(rule.getInactiveDays());
        view.setNoOpportunityDays(rule.getNoOpportunityDays());
        view.setLevels(rule.getLevels());
        view.setDeptId(rule.getDeptId());
        view.setDailyClaimLimit(rule.getDailyClaimLimit());
        view.setEnabled(rule.getEnabled());
        view.setLastRunAt(rule.getLastRunAt());
        view.setRecycleCount(rule.getRecycleCount());
        return view;
    }
}

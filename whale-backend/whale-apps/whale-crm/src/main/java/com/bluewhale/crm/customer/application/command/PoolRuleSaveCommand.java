package com.bluewhale.crm.customer.application.command;

import lombok.Data;

/**
 * 公海规则保存命令。
 *
 * @author BlueWhale
 */
@Data
public class PoolRuleSaveCommand {

    /** 规则 ID；为空表示按规则名 upsert */
    private Long id;

    /** 规则名称 */
    private String ruleName;

    /** 未跟进天数阈值 */
    private Integer inactiveDays;

    /** 无商机天数阈值 */
    private Integer noOpportunityDays;

    /** 适用等级 */
    private String levels;

    /** 适用部门 */
    private Long deptId;

    /** 每日领取上限 */
    private Integer dailyClaimLimit;

    /** 是否启用 */
    private Boolean enabled;
}

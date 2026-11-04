package com.bluewhale.crm.customer.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公海规则实体，对应表 {@code crm_customer_pool_rule}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_pool_rule")
public class CustomerPoolRuleEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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
}

package com.bluewhale.crm.sales.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售目标完成情况视图。
 *
 * @author BlueWhale
 */
@Data
public class TargetCompletionView {

    /** 目标 ID */
    private Long targetId;

    /** 周期 */
    private String period;

    /** 周期类型 */
    private String periodType;

    /** 范围类型 */
    private String scopeType;

    /** 目标金额 */
    private BigDecimal targetAmount;

    /** 已完成金额（赢单商机金额；合同台账上线后切换为签约额） */
    private BigDecimal achievedAmount;

    /** 完成率（百分比，保留两位小数） */
    private BigDecimal completionRate;

    /** 已赢单商机数 */
    private Integer achievedCount;
}

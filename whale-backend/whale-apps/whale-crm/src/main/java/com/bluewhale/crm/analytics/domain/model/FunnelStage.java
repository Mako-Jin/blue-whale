package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售漏斗阶段。
 *
 * @author BlueWhale
 */
@Data
public class FunnelStage {

    /** 阶段编码 */
    private String stage;

    /** 阶段名称 */
    private String stageLabel;

    /** 阶段商机数 */
    private long count;

    /** 阶段金额 */
    private BigDecimal amount;

    /** 相对上一阶段的转化率（百分比，首个阶段为 100） */
    private BigDecimal conversionRate;

    /** 平均停留天数 */
    private BigDecimal avgStayDays;
}

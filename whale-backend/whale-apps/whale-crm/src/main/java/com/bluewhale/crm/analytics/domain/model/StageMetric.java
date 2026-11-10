package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商机阶段统计。
 *
 * @author BlueWhale
 */
@Data
public class StageMetric {

    /** 阶段编码 */
    private String stage;

    /** 进入该阶段的商机数量 */
    private long count;

    /** 阶段金额 */
    private BigDecimal amount;

    /** 平均停留天数 */
    private BigDecimal avgStayDays;
}

package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户满意度统计。
 *
 * @author BlueWhale
 */
@Data
public class CsatSummary {

    /** 平均分 */
    private BigDecimal avgScore;

    /** 低分数量 */
    private long lowCount;

    /** 答卷数量 */
    private long responseCount;
}

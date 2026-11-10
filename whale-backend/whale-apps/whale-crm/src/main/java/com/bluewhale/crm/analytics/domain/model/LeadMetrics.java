package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

/**
 * 线索统计结果。
 *
 * @author BlueWhale
 */
@Data
public class LeadMetrics {

    /** 区间内新增线索数 */
    private long newCount;

    /** 区间内转化线索数 */
    private long convertedCount;
}

package com.bluewhale.lms.credit.domain.model;

import lombok.Data;

/**
 * 学分分布桶（按人合计学分分档）。
 *
 * <p>按<b>人</b>分档而不是按笔：一个人上完三门课各 2 分，属于「6 分档」，
 * 若按笔统计会把同一人算成三笔 2 分，分布图看着很均匀、实际没有一个人超过 5 分。</p>
 *
 * @author BlueWhale
 */
@Data
public class CreditBucket {

    /** 档位标签（如 0-5 / 5-10 / 10-20 / 20+，左闭右开，末档为上不封顶） */
    private String label;

    /** 该档人数 */
    private long earners;
}

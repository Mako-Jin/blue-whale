package com.bluewhale.party.fee.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 党费收缴统计视图（M-P4，按所属月份）。
 *
 * <p>免缴账单两边都不计入：免缴是「经支部研究同意不缴」，把它放进分母会把
 * 一个不可能完成的账单算成欠缴，收缴率永远到不了 100%。</p>
 *
 * @author BlueWhale
 */
@Data
public class FeeMetricView {

    /** 应缴金额合计（排除免缴） */
    private BigDecimal dueAmount = BigDecimal.ZERO;

    /** 实缴金额合计（排除免缴） */
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** 应缴账单数（排除免缴） */
    private long dueBillCount;

    /** 已结清账单数 */
    private long paidBillCount;
}

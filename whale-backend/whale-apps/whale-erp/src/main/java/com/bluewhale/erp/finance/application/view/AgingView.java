package com.bluewhale.erp.finance.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 账龄分析行（按往来单位汇总）。
 *
 * <p>账龄区间：未到期、1–30 天、31–60 天、61–90 天、90 天以上。</p>
 *
 * @author BlueWhale
 */
@Data
public class AgingView {

    /** 维度：AR 应收 / AP 应付 */
    private String dimension;

    /** 往来单位 ID */
    private Long partyId;

    /** 往来单位名称 */
    private String partyName;

    /** 未结余额合计 */
    private BigDecimal total;

    /** 未到期 */
    private BigDecimal notDue;

    /** 逾期 1–30 天 */
    private BigDecimal overdue1To30;

    /** 逾期 31–60 天 */
    private BigDecimal overdue31To60;

    /** 逾期 61–90 天 */
    private BigDecimal overdue61To90;

    /** 逾期 90 天以上 */
    private BigDecimal overdueOver90;
}

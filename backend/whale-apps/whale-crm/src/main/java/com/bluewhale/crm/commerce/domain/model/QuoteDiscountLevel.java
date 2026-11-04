package com.bluewhale.crm.commerce.domain.model;

import java.math.BigDecimal;

/**
 * 报价折扣审批层级（D6）。
 *
 * <p>≤5% 主管；5%–15% 销售总监；&gt;15% 总经理。</p>
 *
 * @author BlueWhale
 */
public enum QuoteDiscountLevel {

    /** 主管 */
    SUPERVISOR,

    /** 销售总监 */
    DIRECTOR,

    /** 总经理 */
    GENERAL_MANAGER;

    /** 主管审批上限（含） */
    private static final BigDecimal SUPERVISOR_LIMIT = new BigDecimal("5");

    /** 销售总监审批上限（含） */
    private static final BigDecimal DIRECTOR_LIMIT = new BigDecimal("15");

    /**
     * 按综合折扣率解析审批层级。
     *
     * @param rate 折扣率（百分比）
     * @return 审批层级
     */
    public static QuoteDiscountLevel resolve(BigDecimal rate) {
        BigDecimal effective = rate == null ? BigDecimal.ZERO : rate;
        if (effective.compareTo(SUPERVISOR_LIMIT) <= 0) {
            return SUPERVISOR;
        }
        if (effective.compareTo(DIRECTOR_LIMIT) <= 0) {
            return DIRECTOR;
        }
        return GENERAL_MANAGER;
    }
}

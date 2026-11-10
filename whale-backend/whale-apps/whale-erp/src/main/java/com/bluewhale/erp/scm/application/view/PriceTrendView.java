package com.bluewhale.erp.scm.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料采购价格趋势视图（按月汇总）。
 *
 * @author BlueWhale
 */
@Data
public class PriceTrendView {

    /** 月份（yyyy-MM） */
    private String month;

    /** 加权平均单价 */
    private BigDecimal avgPrice;

    /** 最低单价 */
    private BigDecimal minPrice;

    /** 最高单价 */
    private BigDecimal maxPrice;

    /** 成交笔数 */
    private Integer orderCount;
}
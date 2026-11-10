package com.bluewhale.mall.order.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 运营日报表行视图。
 *
 * @author BlueWhale
 */
@Data
public class DailyReportView {

    /** 统计日期 */
    private LocalDate statDate;

    /** 订单数 */
    private long orderCount;

    /** 商品总额 */
    private BigDecimal goodsAmount;

    /** 运费总额 */
    private BigDecimal freightAmount;

    /** 优惠总额 */
    private BigDecimal couponDiscount;

    /** 实付总额（含运费） */
    private BigDecimal paidAmount;

    /** 退款总额 */
    private BigDecimal refundAmount;
}

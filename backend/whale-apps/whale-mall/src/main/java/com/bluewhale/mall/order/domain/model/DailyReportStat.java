package com.bluewhale.mall.order.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 运营日报表统计行（按日聚合订单头）。
 *
 * <p>直接聚合 {@code mall_order}，不建快照表：日粒度聚合对全表索引扫描
 * 是一次秒级查询，报表本身不看历史时点，快照表只会引入「快照时间点之后
 * 订单退款了，报表还对不对」这类口径争议。</p>
 *
 * @author BlueWhale
 */
@Data
public class DailyReportStat {

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

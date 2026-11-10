package com.bluewhale.erp.analytics.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 驾驶舱视图。
 *
 * @author BlueWhale
 */
@Data
public class CockpitView {

    /** 快照编号 */
    private String snapshotNo;

    /** 统计期间 */
    private String periodCode;

    /** 统计截止日期 */
    private LocalDate statDate;

    /** 收入 */
    private BigDecimal revenueAmount;

    /** 成本 */
    private BigDecimal costAmount;

    /** 利润 */
    private BigDecimal profitAmount;

    /** 订单数 */
    private Integer orderCount;

    /** 未关闭预警数 */
    private Integer alertCount;

    /** 快照状态 */
    private String status;

    /** 刷新时间 */
    private LocalDateTime refreshedAt;
}

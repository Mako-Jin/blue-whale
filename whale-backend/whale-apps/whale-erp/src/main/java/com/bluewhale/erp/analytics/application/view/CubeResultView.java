package com.bluewhale.erp.analytics.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 多维分析结果视图。
 *
 * @author BlueWhale
 */
@Data
public class CubeResultView {

    /** 维度 */
    private String dimensionType;

    /** 指标 */
    private String metricType;

    /** 日期起 */
    private LocalDate fromDate;

    /** 日期止 */
    private LocalDate toDate;

    /** 明细行 */
    private List<CubeRowView> rows;

    /** 指标合计 */
    private BigDecimal totalMetric;

    /** 订单数合计 */
    private Integer totalOrderCount;

    /** 说明（预算数据缺失等提示） */
    private String note;
}

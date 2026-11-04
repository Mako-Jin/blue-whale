package com.bluewhale.erp.analytics.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 多维分析明细行视图。
 *
 * @author BlueWhale
 */
@Data
public class CubeRowView {

    /** 维度键（客户 / 物料编码 / 仓库ID） */
    private String dimensionKey;

    /** 维度名称 */
    private String dimensionName;

    /** 指标值 */
    private BigDecimal metricValue;

    /** 该维度订单数 */
    private Integer orderCount;
}

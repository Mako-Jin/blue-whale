package com.bluewhale.crm.sales.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 漏斗阶段视图。
 *
 * @author BlueWhale
 */
@Data
public class FunnelStageView {

    /** 阶段 */
    private String stage;

    /** 商机数量 */
    private long count;

    /** 金额合计 */
    private BigDecimal amount;

    /** 相对上一阶段的转化率（百分比，保留两位小数） */
    private BigDecimal conversionRate;
}

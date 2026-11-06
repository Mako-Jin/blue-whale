package com.bluewhale.erp.mfg.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * MRP 需求追溯视图。
 *
 * @author BlueWhale
 */
@Data
public class MrpTraceView {

    /** 来源需求行号 */
    private Integer demandLineNo;

    /** BOM 路径 */
    private String bomPath;

    /** BOM 层级 */
    private Integer depth;

    /** 单位累计用量（含损耗） */
    private BigDecimal unitQty;

    /** 损耗率（0–100 百分比） */
    private BigDecimal lossRate;

    /** 贡献毛需求量 */
    private BigDecimal grossQuantity;
}

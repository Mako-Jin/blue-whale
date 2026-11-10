package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * MRP 需求追溯行：计划订单 → 来源需求行 + BOM 路径。
 *
 * @author BlueWhale
 */
@Data
public class MrpTrace {

    /** 主键 */
    private Long id;

    /** 运行记录 ID */
    private Long runId;

    /** 计划订单 ID */
    private Long plannedOrderId;

    /** 来源需求行号 */
    private Integer demandLineNo;

    /** BOM 编号 */
    private String bomNo;

    /** BOM 版本 */
    private String bomVersion;

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

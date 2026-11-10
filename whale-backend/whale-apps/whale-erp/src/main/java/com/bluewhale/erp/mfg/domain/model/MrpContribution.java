package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * BOM 展开贡献行：某需求行对某子件的用量贡献。
 *
 * @author BlueWhale
 */
@Data
public class MrpContribution {

    /** 子件编码 */
    private String childPartNo;

    /** 子件名称 */
    private String childPartName;

    /** 计量单位 */
    private String uom;

    /** BOM 路径 */
    private String bomPath;

    /** BOM 层级 */
    private Integer depth;

    /** 单位累计用量（含损耗） */
    private BigDecimal unitQty;

    /** 损耗率（0–100 百分比） */
    private BigDecimal lossRate;

    /** 贡献毛需求量 = 需求数量 × 单位累计用量 */
    private BigDecimal grossQuantity;
}

package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * PDM BOM 快照行（ERP 侧值对象）。
 *
 * <p>路径深度优先，形如 {@code 1} / {@code 1.1}；损耗率为 0–100 百分比。</p>
 *
 * @author BlueWhale
 */
@Data
public class BomLine {

    /** 行路径 */
    private String path;

    /** 层级（根子件为 1） */
    private Integer depth;

    /** 父件编码 */
    private String parentPartNo;

    /** 子件编码 */
    private String childPartNo;

    /** 子件名称 */
    private String childPartName;

    /** 用量 */
    private BigDecimal qty;

    /** 计量单位 */
    private String uom;

    /** 损耗率（0–100 百分比） */
    private BigDecimal lossRate;
}

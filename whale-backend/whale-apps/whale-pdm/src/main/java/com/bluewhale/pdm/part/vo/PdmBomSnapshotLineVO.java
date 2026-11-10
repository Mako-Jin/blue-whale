package com.bluewhale.pdm.part.vo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM 快照行
 *
 * @author BlueWhale
 */
@Data
@JsonPropertyOrder({"path", "depth", "parentPartNo", "childPartNo", "childPartName", "qty", "uom",
        "lossRate", "material", "spec", "remark"})
public class PdmBomSnapshotLineVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 行路径，如 1 / 1.1 */
    private String path;

    /** 层级，根子件为 1 */
    private Integer depth;

    /** 父件编码 */
    private String parentPartNo;

    /** 子件编码 */
    private String childPartNo;

    /** 子件名称 */
    private String childPartName;

    /** 用量 */
    private BigDecimal qty;

    /** 单位 */
    private String uom;

    /** 损耗率 */
    private BigDecimal lossRate;

    /** 材质 */
    private String material;

    /** 规格 */
    private String spec;

    /** 备注 */
    private String remark;
}

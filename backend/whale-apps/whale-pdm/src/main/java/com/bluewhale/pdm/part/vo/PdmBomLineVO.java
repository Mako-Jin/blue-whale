package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM 行视图
 *
 * @author BlueWhale
 */
@Data
public class PdmBomLineVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 行ID */
    private Long id;

    /** 父件ID */
    private Long parentPartId;

    /** 父件编码 */
    private String parentPartNo;

    /** 子件ID */
    private Long childPartId;

    /** 子件编码 */
    private String childPartNo;

    /** 子件名称 */
    private String childPartName;

    /** 行序号 */
    private Integer lineNo;

    /** 用量 */
    private BigDecimal qty;

    /** 单位 */
    private String uom;

    /** 损耗率 */
    private BigDecimal lossRate;

    /** 备注 */
    private String remark;
}

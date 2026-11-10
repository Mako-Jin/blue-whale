package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM 导出行
 *
 * @author BlueWhale
 */
@Data
public class PdmBomExportRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 行路径，如 1 / 1.1 */
    private String path;

    /** 同级行序号（根为空） */
    private Integer lineNo;

    /** 零件编码 */
    private String partNo;

    /** 零件名称 */
    private String partName;

    /** 零件类型 */
    private String partType;

    /** 用量（根为空） */
    private BigDecimal qty;

    /** 单位 */
    private String uom;

    /** 损耗率（%） */
    private BigDecimal lossRate;

    /** 材质 */
    private String material;

    /** 规格 */
    private String spec;

    /** 备注 */
    private String remark;
}

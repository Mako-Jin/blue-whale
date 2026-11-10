package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * BOM 树节点视图
 *
 * @author BlueWhale
 */
@Data
public class PdmBomTreeNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 零件ID */
    private Long partId;

    /** 零件编码 */
    private String partNo;

    /** 零件名称 */
    private String partName;

    /** 零件类型 */
    private String partType;

    /** 单位 */
    private String uom;

    /** 用量（根节点为空） */
    private BigDecimal qty;

    /** 损耗率 */
    private BigDecimal lossRate;

    /** 行序号 */
    private Integer lineNo;

    /** 行ID */
    private Long lineId;

    /** 路径，如 1 / 1.1 */
    private String path;

    /** 层级，根为 0 */
    private Integer depth;

    /** 子节点 */
    private List<PdmBomTreeNodeVO> children = new ArrayList<>();
}

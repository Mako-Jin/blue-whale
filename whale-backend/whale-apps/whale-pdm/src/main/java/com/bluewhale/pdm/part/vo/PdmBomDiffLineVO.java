package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * BOM 对比明细
 *
 * @author BlueWhale
 */
@Data
public class PdmBomDiffLineVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 行路径，如 1 / 1.1 */
    private String path;

    /** 变更类型：ADDED/REMOVED/QTY_CHANGED/LOSS_CHANGED/UOM_CHANGED/PART_CHANGED */
    private String changeType;

    /** 零件编码（新增取目标、删除取基线、变更取目标） */
    private String partNo;

    /** 零件名称 */
    private String partName;

    /** 变更字段 */
    private String field;

    /** 基线值 */
    private String baseValue;

    /** 目标值 */
    private String targetValue;
}

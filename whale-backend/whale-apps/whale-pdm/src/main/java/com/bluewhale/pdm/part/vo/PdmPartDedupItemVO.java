package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 去重候选中的单个零件
 *
 * @author BlueWhale
 */
@Data
public class PdmPartDedupItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 零件ID */
    private Long id;

    /** 零件编码 */
    private String code;

    /** 零件名称 */
    private String name;

    /** 零件类型 */
    private String partType;

    /** 材质 */
    private String material;

    /** 规格 */
    private String spec;

    /** 计量单位 */
    private String uom;

    /** 来源类型 */
    private String sourceType;

    /** 状态 */
    private String status;

    /** ERP 物料编码映射 */
    private String erpItemCode;

    /** 创建时间 */
    private LocalDateTime createTime;
}

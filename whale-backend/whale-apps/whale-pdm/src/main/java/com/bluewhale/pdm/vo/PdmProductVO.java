package com.bluewhale.pdm.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 产品视图对象
 *
 * @author BlueWhale
 */
@Data
public class PdmProductVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 产品编码 */
    private String code;

    /** 产品名称 */
    private String name;

    /** 零件类型：PRODUCT/ASSEMBLY/PART/RAW_MATERIAL */
    private String partType;

    /** 材质 */
    private String material;

    /** 规格 */
    private String spec;

    /** 计量单位 */
    private String uom;

    /** 物料分类编码 */
    private String categoryCode;

    /** ERP 物料编码映射 */
    private String erpItemCode;

    /** 来源类型：STANDARD/COMMON/UNIQUE */
    private String sourceType;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 合并目标零件ID（非空表示已被合并停用） */
    private Long mergedTo;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

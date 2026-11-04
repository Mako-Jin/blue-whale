package com.bluewhale.pdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品实体，对应表 {@code pdm_product}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code pdm_product} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_product")
public class PdmProduct extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 产品编码（租户内唯一） */
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

    /** 备注 */
    private String remark;
}

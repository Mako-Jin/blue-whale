package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 生产订单用料实体，对应表 {@code erp_mfg_production_material}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_production_material")
public class ProductionMaterialEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 生产订单 ID */
    private Long orderId;

    /** 行号 */
    private Integer lineNo;

    /** 物料 ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 需求数量 */
    private BigDecimal requiredQuantity;

    /** 已领数量 */
    private BigDecimal issuedQuantity;
}

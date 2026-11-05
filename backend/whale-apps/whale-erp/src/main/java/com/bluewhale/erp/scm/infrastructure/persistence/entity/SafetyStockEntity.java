package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 安全库存实体，对应表 {@code erp_scm_safety_stock}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_safety_stock")
public class SafetyStockEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 仓库主键 */
    private Long warehouseId;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 安全库存量 */
    private BigDecimal safetyQty;

    /** 最高库存量 */
    private BigDecimal maxQty;

    /** 备注 */
    private String remark;
}

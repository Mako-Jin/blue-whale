package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存移动流水实体，对应表 {@code erp_scm_stock_movement}（只追加）。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_stock_movement")
public class StockMovementEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 单据号 */
    private String docNo;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据号 */
    private String sourceId;

    /** 物料 ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 仓库 ID */
    private Long warehouseId;

    /** 方向 */
    private String direction;

    /** 数量 */
    private BigDecimal quantity;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 金额 */
    private BigDecimal amount;

    /** 发生时间 */
    private LocalDateTime occurredAt;

    /** 备注 */
    private String remark;
}

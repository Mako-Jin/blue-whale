package com.bluewhale.erp.scm.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存移动流水（只追加，不可修改或删除）。
 *
 * @author BlueWhale
 */
@Data
public class StockMovement {

    /** 主键 */
    private Long id;

    /** 关联单据号 */
    private String docNo;

    /** 来源类型：GOODS_RECEIPT / SHIPMENT / STOCK_ADJUST */
    private String sourceType;

    /** 来源单据号（幂等键） */
    private String sourceId;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 仓库主键 */
    private Long warehouseId;

    /** 方向：IN / OUT */
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

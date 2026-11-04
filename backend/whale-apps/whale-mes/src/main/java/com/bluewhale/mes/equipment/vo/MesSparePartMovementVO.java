package com.bluewhale.mes.equipment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 备件流水视图
 *
 * @author BlueWhale
 */
@Data
public class MesSparePartMovementVO {

    /** 流水号 */
    private String movementNo;

    /** 备件编码 */
    private String partCode;

    /** 仓库编码 */
    private String warehouseCode;

    /** 类型 */
    private String movementType;

    /** 变动数量 */
    private BigDecimal quantity;

    /** 变动前数量 */
    private BigDecimal beforeQuantity;

    /** 变动后数量 */
    private BigDecimal afterQuantity;

    /** 关联单号 */
    private String refNo;

    /** 操作人 */
    private String operator;

    /** 发生时间 */
    private LocalDateTime occurredAt;

    /** 备注 */
    private String remark;
}

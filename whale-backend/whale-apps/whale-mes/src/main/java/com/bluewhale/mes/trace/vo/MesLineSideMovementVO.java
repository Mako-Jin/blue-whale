package com.bluewhale.mes.trace.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 线边仓流水视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesLineSideMovementVO {

    /** 流水 ID */
    private Long id;

    /** 流水号 */
    private String movementNo;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码 */
    private String lineCode;

    /** 线边仓编码 */
    private String warehouseCode;

    /** 物料编码 */
    private String itemCode;

    /** 批次号 */
    private String batchNo;

    /** 流水类型 */
    private String movementType;

    /** 方向：1 增加 / -1 扣减 */
    private Integer direction;

    /** 变动数量（正数） */
    private BigDecimal quantity;

    /** 变动前数量 */
    private BigDecimal beforeQuantity;

    /** 变动后数量 */
    private BigDecimal afterQuantity;

    /** 来源类型 */
    private String sourceType;

    /** 来源单号 */
    private String sourceNo;

    /** 关联工单 ID */
    private Long workOrderId;

    /** 关联任务 ID */
    private Long taskId;

    /** 操作人 */
    private String operator;

    /** 备注 */
    private String remark;

    /** 发生时间 */
    private LocalDateTime occurredAt;
}

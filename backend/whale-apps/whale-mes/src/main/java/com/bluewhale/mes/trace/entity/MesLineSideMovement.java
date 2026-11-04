package com.bluewhale.mes.trace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 线边仓流水实体，对应表 {@code mes_tr_line_side_movement}。
 *
 * <p>流水只追加，保留变动前/后余额快照，支撑对账与追溯。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_tr_line_side_movement")
public class MesLineSideMovement extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流水号（租户内唯一） */
    private String movementNo;

    /** 线边库存 ID */
    private Long stockId;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码快照 */
    private String lineCode;

    /** 线边仓编码 */
    private String warehouseCode;

    /** 物料编码 */
    private String itemCode;

    /** 批次号 */
    private String batchNo;

    /** 类型：INBOUND / ISSUE / RETURN / ADJUST */
    private String movementType;

    /** 方向：1 增加 / -1 扣减 */
    private Integer direction;

    /** 变动数量（正数） */
    private BigDecimal quantity;

    /** 变动前数量 */
    private BigDecimal beforeQuantity;

    /** 变动后数量 */
    private BigDecimal afterQuantity;

    /** 来源：MANUAL / ISSUE / RETURN / REPLENISH */
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

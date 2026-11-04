package com.bluewhale.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 备件流水实体，对应表 {@code mes_eq_spare_part_movement}。
 *
 * <p>只追加不修改：每笔入库/领用/退回/调整都记录变动前后数量，用于盘点与追溯。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_eq_spare_part_movement")
public class MesSparePartMovement extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流水号（租户内唯一） */
    private String movementNo;

    /** 备件 ID */
    private Long partId;

    /** 备件编码快照 */
    private String partCode;

    /** 库存行 ID */
    private Long stockId;

    /** 仓库编码 */
    private String warehouseCode;

    /** 类型：IN / OUT / RETURN / ADJUST */
    private String movementType;

    /** 变动数量（正数） */
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

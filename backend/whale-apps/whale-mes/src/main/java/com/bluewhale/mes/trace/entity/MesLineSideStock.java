package com.bluewhale.mes.trace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 线边仓库存实体，对应表 {@code mes_tr_line_side_stock}。
 *
 * <p>按“产线 + 线边仓 + 物料 + 批次”维度记录余额，库存数量不得为负；
 * 变动一律通过线边流水追加，余额与流水同事务更新。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_tr_line_side_stock")
public class MesLineSideStock extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码快照 */
    private String lineCode;

    /** 线边仓编码 */
    private String warehouseCode;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 批次号（无批次为空串） */
    private String batchNo;

    /** 计量单位 */
    private String uom;

    /** 当前库存数量 */
    private BigDecimal quantity;

    /** 安全库存（低于即建议叫料） */
    private BigDecimal safetyQuantity;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}

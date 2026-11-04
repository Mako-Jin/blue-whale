package com.bluewhale.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 备件库存实体，对应表 {@code mes_eq_spare_part_stock}。
 *
 * <p>按备件 + 仓库汇总，余额不得为负；乐观锁防止并发领用超扣。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_eq_spare_part_stock")
public class MesSparePartStock extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 备件 ID */
    private Long partId;

    /** 备件编码快照 */
    private String partCode;

    /** 仓库编码 */
    private String warehouseCode;

    /** 库存数量（不得为负） */
    private BigDecimal quantity;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}

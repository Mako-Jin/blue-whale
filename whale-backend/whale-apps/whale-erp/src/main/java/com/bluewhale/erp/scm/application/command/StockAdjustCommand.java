package com.bluewhale.erp.scm.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存调整命令。
 *
 * <p>正数表示盘盈入库，负数表示盘亏出库；调整金额按当前移动加权平均成本估算。</p>
 *
 * @author BlueWhale
 */
@Data
public class StockAdjustCommand {

    /** 物料编码 */
    @NotNull
    private String itemCode;

    /** 仓库主键 */
    @NotNull
    private Long warehouseId;

    /** 调整数量（正入负出） */
    @NotNull
    private BigDecimal quantity;

    /** 备注 */
    private String remark;
}

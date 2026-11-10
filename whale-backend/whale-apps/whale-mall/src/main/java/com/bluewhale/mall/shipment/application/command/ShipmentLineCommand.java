package com.bluewhale.mall.shipment.application.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发货明细行：发哪一行、发几件。
 *
 * @author BlueWhale
 */
@Data
public class ShipmentLineCommand {

    /** 订单明细 ID */
    @NotNull(message = "请指定订单明细")
    private Long orderItemId;

    /** 本次发货数量 */
    @NotNull(message = "请填写发货数量")
    @Min(value = 1, message = "发货数量必须大于 0")
    private Integer quantity;
}

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
    @NotNull(message = "{validation.mall.shipment-line.order-item-id-required}")
    private Long orderItemId;

    /** 本次发货数量 */
    @NotNull(message = "{validation.mall.shipment-line.quantity-required}")
    @Min(value = 1, message = "{validation.mall.shipment-line.quantity-min}")
    private Integer quantity;
}

package com.bluewhale.mall.shipment.application.command;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作废发货单。
 *
 * @author BlueWhale
 */
@Data
public class ShipmentCancelCommand {

    /** 作废原因 */
    @Size(max = 255, message = "{validation.mall.shipment-cancel.reason-size}")
    private String reason;
}

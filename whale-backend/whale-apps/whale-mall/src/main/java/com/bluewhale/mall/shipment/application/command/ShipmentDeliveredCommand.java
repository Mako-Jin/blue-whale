package com.bluewhale.mall.shipment.application.command;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 签收回调（内网，供物流平台/轨迹服务调用）。
 *
 * @author BlueWhale
 */
@Data
public class ShipmentDeliveredCommand {

    /** 发货单号 */
    @NotBlank(message = "请提供发货单号")
    @Size(max = 32, message = "发货单号不得超过 32 字符")
    private String shipmentNo;

    /** 签收时间：为空取服务端当前时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveredTime;
}

package com.bluewhale.mall.shipment.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 建发货单并发货。
 *
 * <p>建单与发货是同一步，没有草稿态：草稿意味着「占没占可发数量、占没占运单号」
 * 要另定一套规则，而运单号一旦向承运商申请就已经是消耗掉的资源。</p>
 *
 * @author BlueWhale
 */
@Data
public class ShipmentCreateCommand {

    /** 订单 ID */
    @NotNull(message = "{validation.mall.shipment.order-id-required}")
    private Long orderId;

    /** 承运商编码（{@code CarrierType}） */
    @NotBlank(message = "{validation.mall.shipment.carrier-code-required}")
    private String carrierCode;

    /** 承运商名称：{@code OTHER} 时用于显示自定名称，其余可空（取枚举默认名） */
    @Size(max = 64, message = "{validation.mall.shipment.carrier-name-size}")
    private String carrierName;

    /** 运单号（必填，自送也填内部单号） */
    @NotBlank(message = "{validation.mall.shipment.tracking-no-required}")
    @Size(max = 64, message = "{validation.mall.shipment.tracking-no-size}")
    private String trackingNo;

    /** 备注 */
    @Size(max = 255, message = "{validation.mall.shipment.remark-size}")
    private String remark;

    /** 发货明细 */
    @Valid
    @NotEmpty(message = "{validation.mall.shipment.items-required}")
    private List<ShipmentLineCommand> items;
}

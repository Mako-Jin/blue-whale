package com.bluewhale.mall.shipment.application.view;

import lombok.Data;

/**
 * 发货单明细视图。
 *
 * @author BlueWhale
 */
@Data
public class ShipmentItemView {

    /** 订单明细 ID */
    private Long orderItemId;

    /** 商品 ID */
    private Long spuId;

    /** SKU ID */
    private Long skuId;

    /** 商品名称 */
    private String spuName;

    /** SKU 编码 */
    private String skuCode;

    /** 规格 */
    private String specJson;

    /** 本单发货数量 */
    private Integer quantity;
}

package com.bluewhale.mall.shipment.domain.model;

import lombok.Data;

/**
 * 发货单查询条件。
 *
 * @author BlueWhale
 */
@Data
public class ShipmentQuery {

    /** 关键字：发货单号 / 订单号 / 运单号 */
    private String keyword;

    /** 状态 */
    private String status;

    /** 承运商编码 */
    private String carrierCode;

    /** 订单 ID */
    private Long orderId;
}

package com.bluewhale.mall.shipment.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 发货单明细实体，对应表 {@code mall_shipment_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_shipment_item")
public class ShipmentItemEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 发货单 ID */
    private Long shipmentId;

    /** 订单 ID */
    private Long orderId;

    /** 订单明细 ID */
    private Long orderItemId;

    /** 商品 ID */
    private Long spuId;

    /** SKU ID */
    private Long skuId;

    /** 商品名称（快照） */
    private String spuName;

    /** SKU 编码（快照） */
    private String skuCode;

    /** 规格（快照） */
    private String specJson;

    /** 本单发货数量 */
    private Integer quantity;
}

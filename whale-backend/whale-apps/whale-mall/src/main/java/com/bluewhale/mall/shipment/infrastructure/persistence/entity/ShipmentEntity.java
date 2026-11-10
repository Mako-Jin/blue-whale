package com.bluewhale.mall.shipment.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 发货单实体，对应表 {@code mall_shipment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_shipment")
public class ShipmentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 发货单号 */
    private String shipmentNo;

    /** 订单 ID */
    private Long orderId;

    /** 订单号（快照） */
    private String orderNo;

    /** 渠道 ID */
    private Long channelId;

    /** 承运商编码 */
    private String carrierCode;

    /** 承运商名称（快照） */
    private String carrierName;

    /** 运单号 */
    private String trackingNo;

    /** 状态 */
    private String status;

    /** 明细行数 */
    private Integer itemCount;

    /** 发货总件数 */
    private Integer totalQuantity;

    /** 发货时间 */
    private LocalDateTime shippedTime;

    /** 签收时间 */
    private LocalDateTime deliveredTime;

    /** 作废时间 */
    private LocalDateTime cancelTime;

    /** 作废原因 */
    private String cancelReason;

    /** 备注 */
    private String remark;
}

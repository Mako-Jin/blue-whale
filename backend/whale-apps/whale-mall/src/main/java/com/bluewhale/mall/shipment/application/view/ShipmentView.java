package com.bluewhale.mall.shipment.application.view;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 发货单视图。
 *
 * @author BlueWhale
 */
@Data
public class ShipmentView {

    /** 主键 */
    private Long id;

    /** 发货单号 */
    private String shipmentNo;

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 渠道 ID */
    private Long channelId;

    /** 承运商编码 */
    private String carrierCode;

    /** 承运商名称 */
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime shippedTime;

    /** 签收时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveredTime;

    /** 作废时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;

    /** 作废原因 */
    private String cancelReason;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 明细 */
    private List<ShipmentItemView> items = new ArrayList<>();
}

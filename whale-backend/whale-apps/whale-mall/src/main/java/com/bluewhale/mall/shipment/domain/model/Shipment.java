package com.bluewhale.mall.shipment.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 发货单。
 *
 * <p>一张发货单对应<b>一个运单号</b>：运单号是唯一的物理资源，一个包裹一个号。
 * 把两个包裹塞进一张单，等于允许「一单多号」，{@code tracking_no} 从唯一约束
 * 退化成备注，买家用一个号查不到另一个包裹。</p>
 *
 * <p>状态流转收在对象里（{@link #markDelivered} / {@link #cancel}）：
 * 让应用服务直接 {@code setStatus}，状态机就退回成一句口头约定。</p>
 *
 * @author BlueWhale
 */
@Data
public class Shipment {

    /** 运单号最大长度 */
    public static final int TRACKING_NO_MAX_LENGTH = 64;

    /** 承运商名称最大长度 */
    public static final int CARRIER_NAME_MAX_LENGTH = 64;

    /** 作废原因最大长度 */
    public static final int CANCEL_REASON_MAX_LENGTH = 255;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 主键 */
    private Long id;

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
    private ShipmentStatus status;

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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 明细 */
    private List<ShipmentItem> items = new ArrayList<>();

    /**
     * 新建发货单。
     *
     * <p>只校验「这张单自己说得通」的部分：承运商、运单号、明细数量。
     * 够不够发（可发数量）要读订单行，放在应用服务里做——领域对象不该为了
     * 一个校验去背一个仓储依赖。</p>
     *
     * @param shipmentNo 发货单号
     * @param orderId 订单 ID
     * @param orderNo 订单号
     * @param channelId 渠道 ID
     * @param carrier 承运商
     * @param trackingNo 运单号
     * @param items 明细
     * @param shippedAt 发货时间
     * @return 发货单
     */
    public static Shipment create(String shipmentNo, Long orderId, String orderNo, Long channelId,
                                  CarrierType carrier, String trackingNo, List<ShipmentItem> items,
                                  LocalDateTime shippedAt) {
        if (carrier == null) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_CARRIER_INVALID, "请选择承运商");
        }
        String normalizedTracking = normalizeTrackingNo(trackingNo);
        if (items == null || items.isEmpty()) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_INVALID, "发货明细不能为空");
        }
        Set<Long> seen = new HashSet<>();
        int quantity = 0;
        for (ShipmentItem item : items) {
            item.validate();
            if (!seen.add(item.getOrderItemId())) {
                throw MallErrorCode.exception(MallErrorCode.SHIPMENT_INVALID,
                        "同一订单行不能在一张发货单里出现两次");
            }
            quantity += item.quantityOrZero();
        }
        Shipment shipment = new Shipment();
        shipment.setShipmentNo(shipmentNo);
        shipment.setOrderId(orderId);
        shipment.setOrderNo(orderNo);
        shipment.setChannelId(channelId);
        shipment.setCarrierCode(carrier.name());
        shipment.setCarrierName(carrier.label());
        shipment.setTrackingNo(normalizedTracking);
        shipment.setStatus(ShipmentStatus.SHIPPED);
        shipment.setItemCount(items.size());
        shipment.setTotalQuantity(quantity);
        shipment.setShippedTime(shippedAt == null ? LocalDateTime.now() : shippedAt);
        for (ShipmentItem item : items) {
            item.setOrderId(orderId);
        }
        shipment.setItems(items);
        return shipment;
    }

    /**
     * 签收。
     *
     * <p>幂等：已是签收态直接返回 {@code false}（不重写时间）。重复回调是常态
     * ——物流平台的推送重试、运营手点两下都会走到这里，重写时间会让「签收时间」
     * 变成最后一次推送的时间，与对账口径不符。</p>
     *
     * @param deliveredAt 签收时间
     * @return 本次是否发生状态变更
     */
    public boolean markDelivered(LocalDateTime deliveredAt) {
        if (status == ShipmentStatus.DELIVERED) {
            return false;
        }
        if (status == null || !status.canDeliver()) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_STATE_INVALID,
                    "发货单当前状态（" + status + "）不允许签收");
        }
        this.status = ShipmentStatus.DELIVERED;
        this.deliveredTime = deliveredAt == null ? LocalDateTime.now() : deliveredAt;
        return true;
    }

    /**
     * 作废。
     *
     * <p>货没发出去（错发、丢件、运单号填错）时才用。已签收不能作废：
     * 货已经在买家手里，该走售后，而不是把发货单抹掉让账对不上。</p>
     *
     * @param reason 作废原因
     * @param cancelledAt 作废时间
     */
    public void cancel(String reason, LocalDateTime cancelledAt) {
        if (status == null || !status.canCancel()) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_STATE_INVALID,
                    "发货单当前状态（" + status + "）不允许作废");
        }
        String text = reason == null ? null : reason.trim();
        if (text != null && text.length() > CANCEL_REASON_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_INVALID,
                    "作废原因不得超过 " + CANCEL_REASON_MAX_LENGTH + " 字符");
        }
        this.status = ShipmentStatus.CANCELLED;
        this.cancelReason = text;
        this.cancelTime = cancelledAt == null ? LocalDateTime.now() : cancelledAt;
    }

    /**
     * 是否为有效单（未作废）。
     *
     * @return 未作废返回 true
     */
    public boolean effective() {
        return status != null && status.effective();
    }

    /**
     * 是否已签收。
     *
     * @return 已签收返回 true
     */
    public boolean delivered() {
        return status == ShipmentStatus.DELIVERED;
    }

    /**
     * 归一并校验运单号。
     *
     * @param trackingNo 原始运单号
     * @return 去空白后的运单号
     */
    private static String normalizeTrackingNo(String trackingNo) {
        if (trackingNo == null || trackingNo.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_INVALID, "运单号不能为空");
        }
        String value = trackingNo.trim();
        if (value.length() > TRACKING_NO_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_INVALID,
                    "运单号不得超过 " + TRACKING_NO_MAX_LENGTH + " 字符");
        }
        return value;
    }
}

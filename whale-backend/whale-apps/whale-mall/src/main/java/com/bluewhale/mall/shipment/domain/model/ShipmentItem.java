package com.bluewhale.mall.shipment.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

/**
 * 发货单明细：一行订单商品在本单里发了几件。
 *
 * <p>商品名/规格落快照，与 {@code mall_order_item} 同口径：发货单要能独立打印成拣货单，
 * 只存 {@code orderItemId} 的话，订单行被清理后拣货单上就只剩一串数字。</p>
 *
 * @author BlueWhale
 */
@Data
public class ShipmentItem {

    /** 主键 */
    private Long id;

    /** 发货单 ID */
    private Long shipmentId;

    /** 订单 ID */
    private Long orderId;

    /** 订单明细 ID（可发数量按它计算） */
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

    /**
     * 校验本行是否可发货。
     *
     * <p>这里只管「自己这一行说不说得通」（数量为正、订单行已定位）；
     * 够不够发要看订单行的可发数量，那需要加载订单，放在应用服务里做。</p>
     */
    public void validate() {
        if (orderItemId == null) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_ITEM_NOT_FOUND, "发货明细缺少订单行");
        }
        if (quantity == null || quantity <= 0) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_INVALID, "发货数量必须大于 0");
        }
    }

    /**
     * 本行数量。
     *
     * @return 数量；为空返回 0
     */
    public int quantityOrZero() {
        return quantity == null ? 0 : quantity;
    }
}

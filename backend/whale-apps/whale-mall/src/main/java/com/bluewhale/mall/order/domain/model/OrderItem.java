package com.bluewhale.mall.order.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细（商品快照）。
 *
 * <p>商品名、规格、图、折后单价全部复制一份：只存 {@code skuId} 的话，
 * 商品一旦删除或改名，历史订单详情就只剩一串数字，售后与对账都无从谈起。</p>
 *
 * @author BlueWhale
 */
@Data
public class OrderItem {

    /** 主键 */
    private Long id;

    /** 订单 ID */
    private Long orderId;

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

    /** 图片（快照） */
    private String imageUrl;

    /** 成交价目类型 */
    private String priceType;

    /** 成交单价（快照） */
    private BigDecimal unitPrice;

    /** 数量 */
    private Integer quantity;

    /** 已退数量（累计，售后申请时占用） */
    private Integer refundedQuantity;

    /** 已退金额（累计） */
    private BigDecimal refundedAmount;

    /** 小计 */
    private BigDecimal amount;

    /**
     * 当前可退数量。
     *
     * <p>申请售后时前端要按这个数限制可选项；返回 0 说明该行已退完。
     * 数量为 null 时按 0 处理，避免历史数据缺列导致 NPE。</p>
     *
     * @return 可退数量
     */
    public int refundableQuantity() {
        int total = quantity == null ? 0 : quantity;
        int refunded = refundedQuantity == null ? 0 : refundedQuantity;
        return Math.max(total - refunded, 0);
    }
}

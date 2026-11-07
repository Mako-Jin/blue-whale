package com.bluewhale.mall.order.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单行视图（试算与详情共用）。
 *
 * @author BlueWhale
 */
@Data
public class OrderLineView {

    /** 订单明细 ID（申请售后时定位被退的行） */
    private Long id;

    /** SKU ID */
    private Long skuId;

    /** 商品 ID */
    private Long spuId;

    /** 商品名称 */
    private String spuName;

    /** SKU 编码 */
    private String skuCode;

    /** 规格 */
    private String specJson;

    /** 图片 */
    private String imageUrl;

    /** 成交价目类型 */
    private String priceType;

    /** 成交单价 */
    private BigDecimal unitPrice;

    /** 数量 */
    private Integer quantity;

    /** 已退数量（累计） */
    private Integer refundedQuantity;

    /** 当前可退数量（前端据此限制售后可选上限） */
    private Integer refundableQuantity;

    /** 小计 */
    private BigDecimal amount;
}

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

    /** 小计 */
    private BigDecimal amount;
}

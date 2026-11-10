package com.bluewhale.mall.cart.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车行视图（含实时价格与可售状态）。
 *
 * @author BlueWhale
 */
@Data
public class CartItemView {

    /** 条目 ID */
    private Long id;

    /** SKU ID */
    private Long skuId;

    /** SKU 编码 */
    private String skuCode;

    /** 商品 ID */
    private Long spuId;

    /** 商品名称 */
    private String spuName;

    /** 规格描述 */
    private String specJson;

    /** 图片 */
    private String imageUrl;

    /** 实时单价（不可售时为空） */
    private BigDecimal unitPrice;

    /** 成交价目类型 */
    private String priceType;

    /** 数量 */
    private Integer quantity;

    /** 小计（不可售时为空） */
    private BigDecimal amount;

    /** 是否选中 */
    private Boolean selected;

    /** 可售数量 */
    private Integer availableQty;

    /** 是否可购买（商品在售 / 规格启用 / 该渠道有价 / 有库存） */
    private Boolean available;

    /** 不可购买原因 */
    private String unavailableReason;
}

package com.bluewhale.mall.aftersale.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 售后行视图。
 *
 * @author BlueWhale
 */
@Data
public class AfterSaleLineView {

    /** 售后明细 ID */
    private Long id;

    /** 订单明细 ID */
    private Long orderItemId;

    /** 商品 ID */
    private Long spuId;

    /** SKU ID */
    private Long skuId;

    /** 商品名称 */
    private String spuName;

    /** SKU 编码 */
    private String skuCode;

    /** 成交单价 */
    private BigDecimal unitPrice;

    /** 本次退数量 */
    private Integer quantity;

    /** 本次退金额 */
    private BigDecimal amount;
}

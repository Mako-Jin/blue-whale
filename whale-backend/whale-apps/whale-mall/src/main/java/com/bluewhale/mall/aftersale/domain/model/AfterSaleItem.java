package com.bluewhale.mall.aftersale.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 售后明细（商品快照）。
 *
 * <p>商品名、SKU 编码、成交单价都复制一份：商品改名或删除后，
 * 售后单仍要能独立看懂退的是哪件、按什么价退的。</p>
 *
 * <p>{@code orderItemId} 是行级退款额度的占用目标——同一订单行被两张售后单
 * 重复申请时，靠它做条件更新挡第二张。</p>
 *
 * @author BlueWhale
 */
@Data
public class AfterSaleItem {

    /** 主键 */
    private Long id;

    /** 售后单 ID */
    private Long afterSaleId;

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

    /** 成交单价（快照） */
    private BigDecimal unitPrice;

    /** 本次退数量 */
    private Integer quantity;

    /** 本次退金额 = 单价 × 数量 */
    private BigDecimal amount;
}

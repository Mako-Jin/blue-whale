package com.bluewhale.mall.aftersale.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 售后明细实体，对应表 {@code mall_after_sale_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_after_sale_item")
public class AfterSaleItemEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

    /** 本次退金额 */
    private BigDecimal amount;
}

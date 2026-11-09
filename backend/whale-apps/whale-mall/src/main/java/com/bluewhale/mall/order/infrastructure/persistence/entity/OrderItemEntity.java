package com.bluewhale.mall.order.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单明细实体，对应表 {@code mall_order_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_order_item")
public class OrderItemEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 商品 ID */
    private Long spuId;

    /** SKU ID */
    private Long skuId;

    /** 商品类目 ID（快照，品类券判定用） */
    private Long categoryId;

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

    /** 成交单价 */
    private BigDecimal unitPrice;

    /** 数量 */
    private Integer quantity;

    /** 已发数量（累计） */
    private Integer shippedQuantity;

    /** 已退数量（累计） */
    private Integer refundedQuantity;

    /** 已退金额（累计） */
    private BigDecimal refundedAmount;

    /** 小计 */
    private BigDecimal amount;

    /** 分摊优惠金额 */
    private BigDecimal shareDiscount;

    /** 分摊运费 */
    private BigDecimal shareFreight;
}

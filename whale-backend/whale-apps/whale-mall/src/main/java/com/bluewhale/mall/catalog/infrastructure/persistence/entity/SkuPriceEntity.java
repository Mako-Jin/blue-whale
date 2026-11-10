package com.bluewhale.mall.catalog.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 价目实体，对应表 {@code mall_sku_price}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_sku_price")
public class SkuPriceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** SKU ID */
    private Long skuId;

    /** 价目类型 */
    private String priceType;

    /** 价格 */
    private BigDecimal price;
}

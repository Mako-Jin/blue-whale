package com.bluewhale.mall.catalog.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存实体，对应表 {@code mall_sku_stock}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_sku_stock")
public class SkuStockEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** SKU ID */
    private Long skuId;

    /** 可售数量 */
    private Integer availableQty;

    /** 已预占数量 */
    private Integer reservedQty;
}

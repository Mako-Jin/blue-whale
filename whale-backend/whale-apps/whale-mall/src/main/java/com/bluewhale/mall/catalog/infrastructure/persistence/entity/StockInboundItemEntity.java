package com.bluewhale.mall.catalog.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存入库单明细实体，对应表 {@code mall_stock_inbound_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_stock_inbound_item")
public class StockInboundItemEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 入库单 ID */
    private Long inboundId;

    /** SKU ID */
    private Long skuId;

    /** SKU 编码（快照） */
    private String skuCode;

    /** 入库数量 */
    private Integer quantity;
}

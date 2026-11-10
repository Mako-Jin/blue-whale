package com.bluewhale.erp.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存事实只读实体，对应只读视图 {@code v_erp_bi_stock_fact}。
 *
 * @author BlueWhale
 */
@Data
@TableName("v_erp_bi_stock_fact")
public class StockFactEntity {

    /** 物料ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 仓库ID */
    private Long warehouseId;

    /** 结存数量 */
    private BigDecimal quantity;

    /** 结存金额 */
    private BigDecimal amount;
}

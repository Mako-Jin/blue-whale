package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库存余额实体，对应表 {@code erp_scm_stock_balance}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_stock_balance")
public class StockBalanceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 物料 ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 仓库 ID */
    private Long warehouseId;

    /** 数量 */
    private BigDecimal quantity;

    /** 金额 */
    private BigDecimal amount;
}

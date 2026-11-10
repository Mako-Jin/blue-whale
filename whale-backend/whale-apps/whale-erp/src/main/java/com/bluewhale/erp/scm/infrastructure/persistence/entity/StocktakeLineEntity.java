package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库存盘点分录实体，对应表 {@code erp_scm_stocktake_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_stocktake_line")
public class StocktakeLineEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 盘点单 ID */
    private Long stocktakeId;

    /** 行号 */
    private Integer lineNo;

    /** 物料 ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 账面数量 */
    private BigDecimal bookQuantity;

    /** 实盘数量 */
    private BigDecimal countedQuantity;

    /** 差异数量 */
    private BigDecimal difference;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 差异金额 */
    private BigDecimal amount;
}

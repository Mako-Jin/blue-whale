package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库存调拨分录实体，对应表 {@code erp_scm_transfer_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_transfer_line")
public class TransferLineEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 调拨单 ID */
    private Long transferId;

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

    /** 调拨数量 */
    private BigDecimal quantity;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 调拨金额 */
    private BigDecimal amount;
}

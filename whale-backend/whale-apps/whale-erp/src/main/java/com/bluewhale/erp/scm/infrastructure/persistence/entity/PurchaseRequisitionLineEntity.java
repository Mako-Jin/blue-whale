package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购申请分录实体，对应表 {@code erp_scm_purchase_requisition_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_purchase_requisition_line")
public class PurchaseRequisitionLineEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 申请单主键 */
    private Long requisitionId;

    /** 行号 */
    private Integer lineNo;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 申请数量 */
    private BigDecimal quantity;

    /** 预估单价 */
    private BigDecimal unitPrice;

    /** 行金额 */
    private BigDecimal amount;
}

package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购退货单头实体，对应表 {@code erp_scm_purchase_return}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_purchase_return")
public class PurchaseReturnEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 退货单号 */
    private String docNo;

    /** 源采购订单主键 */
    private Long purchaseOrderId;

    /** 供应商主键 */
    private Long supplierId;

    /** 供应商名称快照 */
    private String supplierName;

    /** 退货日期 */
    private LocalDate docDate;

    /** 退货出库仓库 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 退货总额 */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;
}

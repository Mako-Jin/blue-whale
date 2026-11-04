package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购申请单头实体，对应表 {@code erp_scm_purchase_requisition}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_purchase_requisition")
public class PurchaseRequisitionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 申请单号 */
    private String docNo;

    /** 供应商主键 */
    private Long supplierId;

    /** 供应商名称快照 */
    private String supplierName;

    /** 申请日期 */
    private LocalDate docDate;

    /** 需求到货日期 */
    private LocalDate requiredDate;

    /** 收货仓库 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 申请总额 */
    private BigDecimal totalAmount;

    /** 转单生成的采购订单号 */
    private String convertedOrderNo;

    /** 备注 */
    private String remark;
}

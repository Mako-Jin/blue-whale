package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单头实体，对应表 {@code erp_scm_purchase_order}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_purchase_order")
public class PurchaseOrderEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String docNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称快照 */
    private String supplierName;

    /** 单据日期 */
    private LocalDate docDate;

    /** 预计到货日期 */
    private LocalDate expectedDate;

    /** 收货仓库 ID */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 已收货金额 */
    private BigDecimal receivedAmount;

    /** 备注 */
    private String remark;
}

package com.bluewhale.erp.finance.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应付台账视图。
 *
 * @author BlueWhale
 */
@Data
public class PayableView {

    /** 主键 */
    private Long id;

    /** 应付单号 */
    private String docNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 单据日期 */
    private LocalDate docDate;

    /** 到期日 */
    private LocalDate dueDate;

    /** 币种 */
    private String currency;

    /** 应付金额 */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 未结余额 */
    private BigDecimal remainingAmount;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}

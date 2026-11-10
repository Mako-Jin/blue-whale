package com.bluewhale.erp.finance.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收台账视图。
 *
 * @author BlueWhale
 */
@Data
public class ReceivableView {

    /** 主键 */
    private Long id;

    /** 应收单号 */
    private String docNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 单据日期 */
    private LocalDate docDate;

    /** 到期日 */
    private LocalDate dueDate;

    /** 币种 */
    private String currency;

    /** 应收金额 */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 已计提坏账 */
    private BigDecimal badDebtAmount;

    /** 未结余额 */
    private BigDecimal remainingAmount;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}

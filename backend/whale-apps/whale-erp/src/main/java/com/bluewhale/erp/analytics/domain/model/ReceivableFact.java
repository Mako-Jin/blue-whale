package com.bluewhale.erp.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收事实只读投影（来源视图 {@code v_erp_bi_receivable_fact}）。
 *
 * @author BlueWhale
 */
@Data
public class ReceivableFact {

    /** 应收单号 */
    private String docNo;

    /** 客户名称 */
    private String customerName;

    /** 单据日期 */
    private LocalDate docDate;

    /** 到期日 */
    private LocalDate dueDate;

    /** 应收金额 */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 已计提坏账金额 */
    private BigDecimal badDebtAmount;

    /** 状态 */
    private String status;

    /**
     * 未结清金额。
     *
     * @return 应收金额减已核销与坏账
     */
    public BigDecimal outstanding() {
        BigDecimal total = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal settled = settledAmount == null ? BigDecimal.ZERO : settledAmount;
        BigDecimal badDebt = badDebtAmount == null ? BigDecimal.ZERO : badDebtAmount;
        return total.subtract(settled).subtract(badDebt);
    }
}

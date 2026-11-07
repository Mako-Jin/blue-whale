package com.bluewhale.oa.expense.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报销发票领域模型。
 *
 * @author BlueWhale
 */
@Data
public class ExpenseInvoice {

    /** 主键ID */
    private Long id;

    /** 报销单ID */
    private Long expenseId;

    /** 发票代码 */
    private String invoiceCode;

    /** 发票号码 */
    private String invoiceNo;

    /** 开票日期 */
    private LocalDate invoiceDate;

    /** 发票金额 */
    private BigDecimal amount;

    /** 发票税额 */
    private BigDecimal taxAmount;

    /** 发票影像文件ID */
    private Long fileId;
}

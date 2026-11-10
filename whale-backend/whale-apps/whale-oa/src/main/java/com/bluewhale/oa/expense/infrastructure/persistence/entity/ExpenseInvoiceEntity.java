package com.bluewhale.oa.expense.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报销发票实体，对应表 {@code oa_expense_invoice}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_expense_invoice")
public class ExpenseInvoiceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

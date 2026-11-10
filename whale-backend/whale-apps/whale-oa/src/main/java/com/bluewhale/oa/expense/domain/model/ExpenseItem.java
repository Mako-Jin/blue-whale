package com.bluewhale.oa.expense.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报销明细领域模型。
 *
 * @author BlueWhale
 */
@Data
public class ExpenseItem {

    /** 主键ID */
    private Long id;

    /** 报销单ID */
    private Long expenseId;

    /** 行号 */
    private Integer lineNo;

    /** 费用类别编码 */
    private String categoryCode;

    /** 费用发生日期 */
    private LocalDate expenseDate;

    /** 金额 */
    private BigDecimal amount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 费用说明 */
    private String description;
}

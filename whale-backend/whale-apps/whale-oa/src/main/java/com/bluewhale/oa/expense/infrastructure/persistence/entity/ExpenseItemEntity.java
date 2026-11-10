package com.bluewhale.oa.expense.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报销明细实体，对应表 {@code oa_expense_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_expense_item")
public class ExpenseItemEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

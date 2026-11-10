package com.bluewhale.oa.expense.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 费用标准领域模型。
 *
 * @author BlueWhale
 */
@Data
public class ExpenseStandard {

    /** 主键ID */
    private Long id;

    /** 费用类别ID */
    private Long categoryId;

    /** 适用职级/城市编码 */
    private String levelCode;

    /** 限额 */
    private BigDecimal limitAmount;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    private String remark;
}

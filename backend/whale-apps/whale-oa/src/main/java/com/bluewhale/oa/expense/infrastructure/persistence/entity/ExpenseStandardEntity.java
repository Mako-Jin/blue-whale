package com.bluewhale.oa.expense.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 费用标准实体，对应表 {@code oa_expense_standard}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_expense_standard")
public class ExpenseStandardEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 费用类别ID */
    private Long categoryId;

    /** 适用职级/城市编码 */
    private String levelCode;

    /** 限额 */
    private BigDecimal limitAmount;

    /** 是否启用 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}

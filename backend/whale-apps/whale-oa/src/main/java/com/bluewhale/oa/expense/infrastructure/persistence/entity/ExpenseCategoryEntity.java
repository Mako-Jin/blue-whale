package com.bluewhale.oa.expense.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 费用类别实体，对应表 {@code oa_expense_category}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_expense_category")
public class ExpenseCategoryEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 类别编码 */
    private String code;

    /** 类别名称 */
    private String name;

    /** 会计科目 */
    private String accountSubject;

    /** 是否需要发票 */
    private Integer requireInvoice;

    /** 单笔限额 */
    private BigDecimal singleLimit;

    /** 排序号 */
    private Integer sortNo;

    /** 是否启用 */
    private Integer enabled;
}

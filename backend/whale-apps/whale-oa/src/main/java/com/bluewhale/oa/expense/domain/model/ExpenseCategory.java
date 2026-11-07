package com.bluewhale.oa.expense.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 费用类别领域模型。
 *
 * @author BlueWhale
 */
@Data
public class ExpenseCategory {

    /** 主键ID */
    private Long id;

    /** 类别编码 */
    private String code;

    /** 类别名称 */
    private String name;

    /** 会计科目 */
    private String accountSubject;

    /** 是否需要发票 */
    private Boolean requireInvoice;

    /** 单笔限额 */
    private BigDecimal singleLimit;

    /** 排序号 */
    private Integer sortNo;

    /** 是否启用 */
    private Boolean enabled;

    /** 创建时间 */
    private java.time.LocalDateTime createTime;

    /** 更新时间 */
    private java.time.LocalDateTime updateTime;
}

package com.bluewhale.oa.expense.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * ERP 应付单创建请求（OA 报销单审批通过后推送）。
 *
 * @author BlueWhale
 */
@Data
public class ErpPayableRequest {

    /** 来源类型（固定 OA_EXPENSE） */
    private String sourceType;

    /** 来源单据ID（报销单ID） */
    private Long sourceId;

    /** 来源单号 */
    private String sourceCode;

    /** 申请人用户ID */
    private Long applicantId;

    /** 申请部门ID */
    private Long deptId;

    /** 费用类别编码 */
    private String categoryCode;

    /** 应付金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 事由标题 */
    private String title;
}

package com.bluewhale.oa.expense.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 报销单实体，对应表 {@code oa_expense}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_expense")
public class ExpenseEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 报销单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 申请部门ID */
    private Long deptId;

    /** 主费用类别编码 */
    private String categoryCode;

    /** 报销事由标题 */
    private String title;

    /** 合计金额（服务端按明细重算） */
    private BigDecimal totalAmount;

    /** 币种 */
    private String currency;

    /** 状态 */
    private String status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** ERP 推送状态 */
    private String erpPushStatus;

    /** ERP 应付单号 */
    private String erpPayableNo;

    /** 关联出差单ID */
    private Long tripId;

    /** 关联出差预算快照（元） */
    private BigDecimal tripBudgetAmount;

    /** 提交时间 */
    private java.time.LocalDateTime submitTime;

    /** 备注 */
    private String remark;
}

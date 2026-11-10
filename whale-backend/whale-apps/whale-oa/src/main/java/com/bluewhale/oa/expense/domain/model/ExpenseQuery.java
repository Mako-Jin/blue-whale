package com.bluewhale.oa.expense.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 报销单分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExpenseQuery extends PageQuery {

    /** 报销单号（模糊） */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 主费用类别编码 */
    private String categoryCode;

    /** 状态 */
    private String status;

    /** ERP 推送状态 */
    private String erpPushStatus;

    /** 提交日期起（含） */
    private LocalDate dateFrom;

    /** 提交日期止（含） */
    private LocalDate dateTo;
}

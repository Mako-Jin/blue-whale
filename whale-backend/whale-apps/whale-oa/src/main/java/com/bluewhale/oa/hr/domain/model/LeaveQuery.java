package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 请假申请分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaveQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 申请单号（模糊匹配） */
    private String code;

    /** 状态 */
    private String status;

    /** 申请人账号（精确匹配） */
    private String applicant;

    /** 请假类型 */
    private String leaveType;
}

package com.bluewhale.oa.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 请假申请分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OaLeaveQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 申请单号（模糊匹配） */
    private String code;

    /** 状态：PENDING / APPROVED / REJECTED / CANCELLED */
    private String status;

    /** 申请人账号（精确匹配） */
    private String applicant;

    /** 请假类型：ANNUAL / SICK / PERSONAL */
    private String leaveType;
}

package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 员工扩展档案分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmployeeProfileQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 工号（模糊匹配） */
    private String employeeNo;

    /** 部门ID */
    private Long deptId;

    /** 在职状态 */
    private String employmentStatus;
}

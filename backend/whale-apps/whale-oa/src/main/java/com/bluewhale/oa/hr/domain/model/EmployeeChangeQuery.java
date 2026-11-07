package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 人员异动分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmployeeChangeQuery extends PageQuery {

    /** 单号/员工姓名/账号模糊匹配关键字 */
    private String keyword;

    /** 异动类型 */
    private String changeType;

    /** 状态 */
    private String status;

    /** 目标用户ID */
    private Long userId;
}

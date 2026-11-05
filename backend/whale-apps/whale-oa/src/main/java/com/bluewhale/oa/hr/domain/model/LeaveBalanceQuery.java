package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 假期额度分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaveBalanceQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 假期类型ID */
    private Long leaveTypeId;

    /** 额度年份 */
    private Integer balanceYear;
}

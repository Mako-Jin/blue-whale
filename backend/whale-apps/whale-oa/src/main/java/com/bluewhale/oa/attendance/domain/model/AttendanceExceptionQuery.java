package com.bluewhale.oa.attendance.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 考勤异常分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttendanceExceptionQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 异常类型 */
    private String type;

    /** 状态 */
    private String status;

    /** 起始日期 */
    private LocalDate dateFrom;

    /** 结束日期 */
    private LocalDate dateTo;
}

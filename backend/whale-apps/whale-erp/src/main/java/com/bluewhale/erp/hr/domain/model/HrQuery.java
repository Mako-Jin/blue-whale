package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 人力模块分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HrQuery extends PageQuery {

    /** 关键字：编号 / 姓名 / 名称 */
    private String keyword;

    /** 状态 */
    private String status;

    /** 期间（YYYY-MM） */
    private String period;

    /** 组织编码 */
    private String orgCode;

    /** 员工编号 */
    private String employeeNo;

    /** 绩效周期主键 */
    private Long cycleId;

    /** 生效日期起 */
    private LocalDate fromDate;

    /** 生效日期止 */
    private LocalDate toDate;
}

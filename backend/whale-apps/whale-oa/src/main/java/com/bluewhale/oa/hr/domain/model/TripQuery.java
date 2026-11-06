package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 出差申请分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TripQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 申请单号（模糊匹配） */
    private String code;

    /** 状态 */
    private String status;

    /** 申请人账号（精确匹配） */
    private String applicant;

    /** 目的地（模糊匹配） */
    private String destination;

    /** 报告状态 */
    private String reportStatus;

    /** 出差开始日期起（含） */
    private LocalDate dateFrom;

    /** 出差开始日期止（含） */
    private LocalDate dateTo;
}

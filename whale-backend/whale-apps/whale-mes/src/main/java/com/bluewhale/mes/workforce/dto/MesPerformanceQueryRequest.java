package com.bluewhale.mes.workforce.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 绩效统计分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesPerformanceQueryRequest {

    /** 统计层级：DAILY/MONTHLY */
    private String statLevel;

    /** 员工 ID */
    private Long employeeId;

    /** 班组 ID */
    private Long teamId;

    /** 期间编码：日 YYYYMMDD，月 YYYYMM */
    private String periodCode;

    /** 统计日期 */
    private LocalDate workDate;

    /** 锁定标记：0 否 / 1 是 */
    private Integer locked;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}

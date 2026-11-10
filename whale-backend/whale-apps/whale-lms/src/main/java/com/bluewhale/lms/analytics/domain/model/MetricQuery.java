package com.bluewhale.lms.analytics.domain.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * 指标快照查询条件。
 *
 * @author BlueWhale
 */
@Data
public class MetricQuery {

    /** 起始统计日（含） */
    private LocalDate from;

    /** 结束统计日（含） */
    private LocalDate to;

    /** 统计范围（为空不限） */
    private String scopeType;

    /** 页码 */
    private Long current = 1L;

    /** 条数 */
    private Long size = 20L;
}

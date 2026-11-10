package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * 指标快照查询条件。
 *
 * @author BlueWhale
 */
@Data
public class MetricSnapshotQuery {

    /** 指标编码，可空 */
    private String metricCode;

    /** 起始统计日，可空 */
    private LocalDate startDate;

    /** 结束统计日，可空 */
    private LocalDate endDate;

    /** 维度类型，可空 */
    private String dimensionType;

    /** 维度值，可空 */
    private String dimensionId;

    /** 当前页 */
    private long current = 1L;

    /** 每页条数 */
    private long size = 10L;
}

package com.bluewhale.erp.analytics.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 数据分析与报表中心分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AnalyticsQuery extends PageQuery {

    /** 关键字：快照号 / 报表编号 / 计划编号 / 规则编号 / 事件编号 */
    private String keyword;

    /** 状态 */
    private String status;

    /** 统计期间 yyyyMM */
    private String periodCode;

    /** 维度：CUSTOMER / ITEM / WAREHOUSE */
    private String dimensionType;

    /** 指标：REVENUE / QUANTITY / ORDER_COUNT / COST */
    private String metricType;

    /** 规则类型：STOCK_LOW / RECEIVABLE_OVERDUE / QUALITY_OPEN_NCR / BUDGET_EXCEEDED */
    private String ruleType;

    /** 日期起（销售单据日期 / 出库日期） */
    private LocalDate fromDate;

    /** 日期止 */
    private LocalDate toDate;
}

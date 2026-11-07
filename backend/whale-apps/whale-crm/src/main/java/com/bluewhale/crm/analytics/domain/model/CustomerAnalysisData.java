package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 客户分析结果。
 *
 * <p>新增与活跃均按统计日往前 30 天滚动窗口计算；活跃口径为窗口内存在跟进记录的客户。
 * 集中度为最近 90 天赢单金额中前 5 大客户占比。</p>
 *
 * @author BlueWhale
 */
@Data
public class CustomerAnalysisData {

    /** 统计日期 */
    private LocalDate statDate;

    /** 近 30 天新增客户数 */
    private long newCount;

    /** 近 30 天活跃客户数 */
    private long activeCount;

    /** 等级分布 */
    private Map<String, Long> levelDistribution;

    /** 行业分布 */
    private Map<String, Long> industryDistribution;

    /** 来源分布 */
    private Map<String, Long> sourceDistribution;

    /** 区域分布 */
    private Map<String, Long> regionDistribution;

    /** 前 5 大客户赢单金额集中度（百分比） */
    private BigDecimal top5Concentration;
}

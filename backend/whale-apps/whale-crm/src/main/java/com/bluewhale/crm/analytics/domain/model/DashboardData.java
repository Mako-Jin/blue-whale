package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售看板聚合数据。
 *
 * <p>目标完成率 = 当月赢单金额 / 当月目标金额 × 100；预测缺口 =
 * max(0, 当月目标 − 当月赢单 − 加权管道金额)。</p>
 *
 * @author BlueWhale
 */
@Data
public class DashboardData {

    /** 统计日期 */
    private LocalDate statDate;

    /** 今日新增线索 */
    private long todayNewLeads;

    /** 本周赢单金额 */
    private BigDecimal weekWonAmount;

    /** 本月目标金额 */
    private BigDecimal monthTargetAmount;

    /** 本月赢单金额 */
    private BigDecimal monthWonAmount;

    /** 本月目标完成率（百分比） */
    private BigDecimal targetCompletionRate;

    /** 加权管道金额 */
    private BigDecimal weightedPipelineAmount;

    /** 预测缺口 */
    private BigDecimal forecastGap;

    /** 风险商机数（预计关闭日期已过且未关闭） */
    private long riskOpportunityCount;

    /** 未关闭行动项数量 */
    private long openActionCount;
}

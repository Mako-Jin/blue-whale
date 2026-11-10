package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 回款分析结果。
 *
 * <p>实收口径：CRM 暂以回款计划中已到期的计划金额统计，接入 ERP 实收后替换为真实到账金额。
 * 逾期金额取最新应收快照的逾期分段合计。</p>
 *
 * @author BlueWhale
 */
@Data
public class CollectionAnalysisData {

    /** 统计日期 */
    private LocalDate statDate;

    /** 回款实收（当前为已到期计划额） */
    private BigDecimal receivedAmount;

    /** 逾期应收金额 */
    private BigDecimal overdueAmount;

    /** 逾期客户数 */
    private long overdueCustomerCount;

    /** 账龄分布 */
    private Map<String, BigDecimal> agingDistribution;

    /** 重点逾期行动建议 */
    private List<ActionCandidate> keyOverdueCustomers;
}

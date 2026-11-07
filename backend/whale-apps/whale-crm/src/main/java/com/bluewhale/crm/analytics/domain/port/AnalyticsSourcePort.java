package com.bluewhale.crm.analytics.domain.port;

import com.bluewhale.crm.analytics.domain.model.ActionCandidate;
import com.bluewhale.crm.analytics.domain.model.CsatSummary;
import com.bluewhale.crm.analytics.domain.model.CustomerAnalysisData;
import com.bluewhale.crm.analytics.domain.model.ExportTable;
import com.bluewhale.crm.analytics.domain.model.ExportType;
import com.bluewhale.crm.analytics.domain.model.LeadMetrics;
import com.bluewhale.crm.analytics.domain.model.RankingRow;
import com.bluewhale.crm.analytics.domain.model.StageMetric;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 分析数据只读查询端口。
 *
 * <p>实现只允许执行 SELECT，不得写入客户 / 销售 / 商务 / 服务等上下文的数据表；
 * 数据范围由 {@code ownerId} / {@code deptId} 透传，二者为空表示全量。</p>
 *
 * @author BlueWhale
 */
public interface AnalyticsSourcePort {

    /**
     * 统计区间内的线索新增与转化。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 线索统计
     */
    LeadMetrics leadMetrics(LocalDate start, LocalDate end, Long ownerId, Long deptId);

    /**
     * 统计区间内的商机阶段数量、金额与平均停留天数。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 阶段统计
     */
    List<StageMetric> opportunityStageMetrics(LocalDate start, LocalDate end, Long ownerId, Long deptId);

    /**
     * 统计区间内的赢单金额。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 赢单金额
     */
    BigDecimal wonAmount(LocalDate start, LocalDate end, Long ownerId, Long deptId);

    /**
     * 统计区间内的合同台账金额。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 合同金额
     */
    BigDecimal contractAmount(LocalDate start, LocalDate end, Long ownerId, Long deptId);

    /**
     * 统计区间内的订单金额。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 订单金额
     */
    BigDecimal orderAmount(LocalDate start, LocalDate end, Long ownerId, Long deptId);

    /**
     * 统计区间内的开票金额。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 开票金额
     */
    BigDecimal invoiceAmount(LocalDate start, LocalDate end, Long ownerId, Long deptId);

    /**
     * 统计截止日的回款实收（CRM 暂以已到期回款计划额近似）。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 回款金额
     */
    BigDecimal collectionReceivedAmount(LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 统计截止日最新应收快照的逾期金额。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 逾期金额
     */
    BigDecimal arOverdueAmount(LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 统计截止日最新应收快照的账龄分布。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 账龄分布（未到期 / 1-30 天 / 31-60 天 / 61-90 天 / 90 天以上）
     */
    Map<String, BigDecimal> arAging(LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 统计指定日新增客户数。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 新增客户数
     */
    long newCustomerCount(LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 客户分析：近 30 天新增 / 活跃与等级、行业、来源、区域分布及集中度。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 客户分析数据
     */
    CustomerAnalysisData customerAnalysis(LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 统计区间内的销售排行。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 排行行
     */
    List<RankingRow> ranking(LocalDate start, LocalDate end, Long ownerId, Long deptId);

    /**
     * 统计区间内的满意度均值与低分数量。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 满意度统计
     */
    CsatSummary csatSummary(LocalDate start, LocalDate end, Long ownerId, Long deptId);

    /**
     * 查询月度销售目标合计。
     *
     * @param period 周期（yyyy-MM）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 目标金额
     */
    BigDecimal monthlyTargetAmount(String period, Long ownerId, Long deptId);

    /**
     * 查询概率加权管道金额。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 加权金额
     */
    BigDecimal weightedPipelineAmount(LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 查询风险商机数量（预计关闭日期已过且未关闭）。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 风险商机数
     */
    long riskOpportunityCount(LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 查询未关闭商机数量。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 未关闭商机数
     */
    long openOpportunityCount(LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 查询未关闭商机金额。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 未关闭商机金额
     */
    BigDecimal openOpportunityAmount(LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 查询长期未跟进的重点客户。
     *
     * @param thresholdDays 未跟进天数阈值
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 候选行动项
     */
    List<ActionCandidate> idleCustomers(int thresholdDays, LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 查询预计关闭逾期的商机。
     *
     * @param graceDays 宽限天数
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 候选行动项
     */
    List<ActionCandidate> overdueOpportunities(int graceDays, LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 查询月度目标缺口。
     *
     * @param period 周期（yyyy-MM）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 候选行动项
     */
    List<ActionCandidate> targetGaps(String period, Long ownerId, Long deptId);

    /**
     * 查询即将到期的合同。
     *
     * @param days 到期窗口天数
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 候选行动项
     */
    List<ActionCandidate> expiringContracts(int days, LocalDate statDate, Long ownerId, Long deptId);

    /**
     * 查询逾期应收客户。
     *
     * @param amountThreshold 逾期金额阈值
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 候选行动项
     */
    List<ActionCandidate> overdueReceivables(BigDecimal amountThreshold, LocalDate statDate,
                                             Long ownerId, Long deptId);

    /**
     * 查询满意度低分工单。
     *
     * @param maxScore 低分上限
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 候选行动项
     */
    List<ActionCandidate> lowCsatTickets(int maxScore, LocalDate start, LocalDate end,
                                         Long ownerId, Long deptId);

    /**
     * 读取导出数据（只读，手机号 / 邮箱由应用层统一脱敏）。
     *
     * @param type 导出类型
     * @param filtersJson 筛选条件 JSON
     * @param maxRows 单次最大行数（实现可读取 maxRows + 1 行以判断超限）
     * @return 导出数据表
     */
    ExportTable exportTable(ExportType type, String filtersJson, int maxRows);
}

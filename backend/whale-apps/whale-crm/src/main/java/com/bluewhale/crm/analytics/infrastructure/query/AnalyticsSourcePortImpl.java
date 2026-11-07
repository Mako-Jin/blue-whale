package com.bluewhale.crm.analytics.infrastructure.query;

import com.bluewhale.crm.analytics.domain.model.ActionCandidate;
import com.bluewhale.crm.analytics.domain.model.ActionType;
import com.bluewhale.crm.analytics.domain.model.CsatSummary;
import com.bluewhale.crm.analytics.domain.model.CustomerAnalysisData;
import com.bluewhale.crm.analytics.domain.model.ExportTable;
import com.bluewhale.crm.analytics.domain.model.ExportType;
import com.bluewhale.crm.analytics.domain.model.LeadMetrics;
import com.bluewhale.crm.analytics.domain.model.RankingRow;
import com.bluewhale.crm.analytics.domain.model.StageMetric;
import com.bluewhale.crm.analytics.domain.port.AnalyticsSourcePort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析数据只读查询端口实现。
 *
 * <p>全部 SQL 均为 SELECT，不写入客户、销售、商务、服务上下文的数据表。
 * 租户固定为默认租户 1；数据范围通过 ownerId / deptId 动态拼接。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class AnalyticsSourcePortImpl implements AnalyticsSourcePort {

    /** 默认租户 */
    private static final long TENANT_ID = 1L;

    /** 时间格式 */
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 默认导出上限 */
    private static final int DEFAULT_LIMIT = 500;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 统计区间内的线索新增与转化。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 线索统计
     */
    @Override
    public LeadMetrics leadMetrics(LocalDate start, LocalDate end, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder newSql = new StringBuilder("SELECT COUNT(*) FROM crm_lead l"
                + " WHERE l.tenant_id = " + TENANT_ID + " AND l.deleted = 0"
                + " AND l.create_time >= ? AND l.create_time < ?");
        args.add(start);
        args.add(end.plusDays(1));
        appendScope(newSql, args, ownerId, deptId, "l.owner_id", "l.dept_id");
        LeadMetrics metrics = new LeadMetrics();
        metrics.setNewCount(count(newSql.toString(), args));

        List<Object> convertedArgs = new ArrayList<>();
        StringBuilder convertedSql = new StringBuilder("SELECT COUNT(*) FROM crm_lead l"
                + " WHERE l.tenant_id = " + TENANT_ID + " AND l.deleted = 0"
                + " AND l.converted_at >= ? AND l.converted_at < ?");
        convertedArgs.add(start);
        convertedArgs.add(end.plusDays(1));
        appendScope(convertedSql, convertedArgs, ownerId, deptId, "l.owner_id", "l.dept_id");
        metrics.setConvertedCount(count(convertedSql.toString(), convertedArgs));
        return metrics;
    }

    /**
     * 统计区间内的商机阶段数量、金额与平均停留天数。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 阶段统计
     */
    @Override
    public List<StageMetric> opportunityStageMetrics(LocalDate start, LocalDate end, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT t.to_stage AS stage,"
                + " COUNT(DISTINCT t.opportunity_id) AS cnt,"
                + " COALESCE(SUM(t.amount), 0) AS amt,"
                + " COALESCE(AVG(t.days_in_stage), 0) AS avg_days"
                + " FROM (SELECT h.opportunity_id, h.to_stage, h.amount,"
                + " TIMESTAMPDIFF(DAY, h.changed_at,"
                + " COALESCE(LEAD(h.changed_at) OVER (PARTITION BY h.opportunity_id ORDER BY h.changed_at), NOW()))"
                + " AS days_in_stage"
                + " FROM crm_opportunity_stage_history h"
                + " LEFT JOIN crm_opportunity o ON o.id = h.opportunity_id"
                + " AND o.tenant_id = " + TENANT_ID + " AND o.deleted = 0"
                + " WHERE h.tenant_id = " + TENANT_ID + " AND h.deleted = 0"
                + " AND h.changed_at >= ? AND h.changed_at < ?");
        args.add(start);
        args.add(end.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "o.owner_id", "o.dept_id");
        sql.append(") t GROUP BY t.to_stage");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            StageMetric metric = new StageMetric();
            metric.setStage(rs.getString("stage"));
            metric.setCount(rs.getLong("cnt"));
            metric.setAmount(rs.getBigDecimal("amt"));
            metric.setAvgStayDays(rs.getBigDecimal("avg_days"));
            return metric;
        }, args.toArray());
    }

    /**
     * 统计区间内的赢单金额。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 赢单金额
     */
    @Override
    public BigDecimal wonAmount(LocalDate start, LocalDate end, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(o.amount), 0) FROM crm_opportunity o"
                + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0 AND o.stage = 'WON'"
                + " AND o.closed_at >= ? AND o.closed_at < ?");
        args.add(start);
        args.add(end.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "o.owner_id", "o.dept_id");
        return amount(sql.toString(), args);
    }

    /**
     * 统计区间内的合同台账金额。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 合同金额
     */
    @Override
    public BigDecimal contractAmount(LocalDate start, LocalDate end, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(c.amount), 0) FROM crm_contract c"
                + " WHERE c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                + " AND c.create_time >= ? AND c.create_time < ?");
        args.add(start);
        args.add(end.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "c.owner_id", "c.dept_id");
        return amount(sql.toString(), args);
    }

    /**
     * 统计区间内的订单金额。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 订单金额
     */
    @Override
    public BigDecimal orderAmount(LocalDate start, LocalDate end, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(o.total_amount), 0) FROM crm_sales_order o"
                + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0"
                + " AND o.status IN ('APPROVED', 'PUSHING', 'PUSHED')"
                + " AND o.create_time >= ? AND o.create_time < ?");
        args.add(start);
        args.add(end.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "o.owner_id", "o.dept_id");
        return amount(sql.toString(), args);
    }

    /**
     * 统计区间内的开票金额。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 开票金额
     */
    @Override
    public BigDecimal invoiceAmount(LocalDate start, LocalDate end, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(COALESCE(i.issued_amount, i.amount)), 0)"
                + " FROM crm_invoice_request i"
                + " WHERE i.tenant_id = " + TENANT_ID + " AND i.deleted = 0"
                + " AND i.status IN ('ISSUED', 'SENT')"
                + " AND i.issued_at >= ? AND i.issued_at < ?");
        args.add(start);
        args.add(end.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "i.owner_id", "i.dept_id");
        return amount(sql.toString(), args);
    }

    /**
     * 统计截止日的回款实收（以已到期回款计划额近似）。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 回款金额
     */
    @Override
    public BigDecimal collectionReceivedAmount(LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(p.planned_amount), 0)"
                + " FROM crm_collection_plan p"
                + " WHERE p.tenant_id = " + TENANT_ID + " AND p.deleted = 0 AND p.status = 'OPEN'"
                + " AND p.planned_date < ?");
        args.add(statDate.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "p.owner_id", "p.dept_id");
        return amount(sql.toString(), args);
    }

    /**
     * 统计截止日最新应收快照的逾期金额。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 逾期金额
     */
    @Override
    public BigDecimal arOverdueAmount(LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(a.overdue_1_30 + a.overdue_31_60"
                + " + a.overdue_61_90 + a.overdue_over_90), 0) FROM crm_ar_snapshot a"
                + " WHERE a.tenant_id = " + TENANT_ID + " AND a.deleted = 0"
                + " AND a.snapshot_date = (SELECT MAX(s.snapshot_date) FROM crm_ar_snapshot s"
                + " WHERE s.tenant_id = " + TENANT_ID + " AND s.deleted = 0 AND s.snapshot_date <= ?)");
        args.add(statDate);
        appendScope(sql, args, ownerId, deptId, "a.owner_id", "a.dept_id");
        return amount(sql.toString(), args);
    }

    /**
     * 统计截止日最新应收快照的账龄分布。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 账龄分布
     */
    @Override
    public Map<String, BigDecimal> arAging(LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(a.not_due), 0) AS not_due,"
                + " COALESCE(SUM(a.overdue_1_30), 0) AS d1_30,"
                + " COALESCE(SUM(a.overdue_31_60), 0) AS d31_60,"
                + " COALESCE(SUM(a.overdue_61_90), 0) AS d61_90,"
                + " COALESCE(SUM(a.overdue_over_90), 0) AS d90"
                + " FROM crm_ar_snapshot a"
                + " WHERE a.tenant_id = " + TENANT_ID + " AND a.deleted = 0"
                + " AND a.snapshot_date = (SELECT MAX(s.snapshot_date) FROM crm_ar_snapshot s"
                + " WHERE s.tenant_id = " + TENANT_ID + " AND s.deleted = 0 AND s.snapshot_date <= ?)");
        args.add(statDate);
        appendScope(sql, args, ownerId, deptId, "a.owner_id", "a.dept_id");
        Map<String, Object> row = jdbcTemplate.queryForMap(sql.toString(), args.toArray());
        Map<String, BigDecimal> aging = new LinkedHashMap<>();
        aging.put("未到期", decimal(row.get("not_due")));
        aging.put("1-30天", decimal(row.get("d1_30")));
        aging.put("31-60天", decimal(row.get("d31_60")));
        aging.put("61-90天", decimal(row.get("d61_90")));
        aging.put("90天以上", decimal(row.get("d90")));
        return aging;
    }

    /**
     * 统计指定日新增客户数。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 新增客户数
     */
    @Override
    public long newCustomerCount(LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM crm_customer c"
                + " WHERE c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                + " AND c.create_time >= ? AND c.create_time < ?");
        args.add(statDate);
        args.add(statDate.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "c.owner_id", "c.dept_id");
        return count(sql.toString(), args);
    }

    /**
     * 客户分析。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 客户分析数据
     */
    @Override
    public CustomerAnalysisData customerAnalysis(LocalDate statDate, Long ownerId, Long deptId) {
        LocalDate windowStart = statDate.minusDays(30);
        CustomerAnalysisData data = new CustomerAnalysisData();
        data.setStatDate(statDate);
        List<Object> newArgs = new ArrayList<>();
        StringBuilder newSql = new StringBuilder("SELECT COUNT(*) FROM crm_customer c"
                + " WHERE c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                + " AND c.create_time >= ? AND c.create_time < ?");
        newArgs.add(windowStart);
        newArgs.add(statDate.plusDays(1));
        appendScope(newSql, newArgs, ownerId, deptId, "c.owner_id", "c.dept_id");
        data.setNewCount(count(newSql.toString(), newArgs));

        List<Object> activeArgs = new ArrayList<>();
        StringBuilder activeSql = new StringBuilder("SELECT COUNT(DISTINCT f.customer_id) FROM crm_follow_up f"
                + " JOIN crm_customer c ON c.id = f.customer_id"
                + " AND c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                + " WHERE f.tenant_id = " + TENANT_ID + " AND f.deleted = 0"
                + " AND f.create_time >= ? AND f.create_time < ?");
        activeArgs.add(windowStart);
        activeArgs.add(statDate.plusDays(1));
        appendScope(activeSql, activeArgs, ownerId, deptId, "c.owner_id", "c.dept_id");
        data.setActiveCount(count(activeSql.toString(), activeArgs));

        data.setLevelDistribution(distribution("level", "c.level", statDate, ownerId, deptId));
        data.setIndustryDistribution(distribution("industry", "c.industry", statDate, ownerId, deptId));
        data.setSourceDistribution(distribution("source", "c.source", statDate, ownerId, deptId));
        data.setRegionDistribution(regionDistribution(statDate, ownerId, deptId));
        data.setTop5Concentration(top5Concentration(statDate, ownerId, deptId));
        return data;
    }

    /**
     * 统计区间内的销售排行。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 排行行
     */
    @Override
    public List<RankingRow> ranking(LocalDate start, LocalDate end, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT o.owner_id AS owner_id,"
                + " COALESCE(SUM(o.amount), 0) AS won_amount,"
                + " COUNT(*) AS won_count,"
                + " (SELECT COUNT(*) FROM crm_contract c WHERE c.tenant_id = " + TENANT_ID
                + " AND c.deleted = 0 AND c.owner_id = o.owner_id"
                + " AND c.create_time >= ? AND c.create_time < ?) AS contract_count,"
                + " (SELECT COALESCE(SUM(c.amount), 0) FROM crm_contract c WHERE c.tenant_id = " + TENANT_ID
                + " AND c.deleted = 0 AND c.owner_id = o.owner_id"
                + " AND c.create_time >= ? AND c.create_time < ?) AS contract_amount,"
                + " (SELECT COALESCE(SUM(a.total_remaining), 0) FROM crm_ar_snapshot a"
                + " WHERE a.tenant_id = " + TENANT_ID + " AND a.deleted = 0 AND a.owner_id = o.owner_id"
                + " AND a.snapshot_date = (SELECT MAX(s.snapshot_date) FROM crm_ar_snapshot s"
                + " WHERE s.tenant_id = " + TENANT_ID + " AND s.deleted = 0 AND s.snapshot_date <= ?))"
                + " AS remaining"
                + " FROM crm_opportunity o"
                + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0 AND o.stage = 'WON'"
                + " AND o.closed_at >= ? AND o.closed_at < ?");
        args.add(start);
        args.add(end.plusDays(1));
        args.add(start);
        args.add(end.plusDays(1));
        args.add(end);
        args.add(start);
        args.add(end.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "o.owner_id", "o.dept_id");
        sql.append(" GROUP BY o.owner_id ORDER BY won_amount DESC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            RankingRow row = new RankingRow();
            row.setOwnerId(rs.getLong("owner_id"));
            row.setOwnerName("用户" + rs.getLong("owner_id"));
            row.setWonAmount(rs.getBigDecimal("won_amount"));
            row.setWonCount(rs.getLong("won_count"));
            row.setContractCount(rs.getLong("contract_count"));
            BigDecimal contractAmount = rs.getBigDecimal("contract_amount");
            BigDecimal remaining = rs.getBigDecimal("remaining");
            row.setCollectionRate(collectionRate(contractAmount, remaining));
            return row;
        }, args.toArray());
    }

    /**
     * 统计区间内的满意度均值与低分数量。
     *
     * @param start 起始日（含）
     * @param end 结束日（含）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 满意度统计
     */
    @Override
    public CsatSummary csatSummary(LocalDate start, LocalDate end, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(AVG(r.score), 0) AS avg_score,"
                + " COALESCE(SUM(CASE WHEN r.low_score = 1 THEN 1 ELSE 0 END), 0) AS low_count,"
                + " COUNT(*) AS response_count"
                + " FROM crm_satisfaction_response r"
                + " JOIN crm_ticket t ON t.id = r.ticket_id"
                + " AND t.tenant_id = " + TENANT_ID + " AND t.deleted = 0"
                + " WHERE r.tenant_id = " + TENANT_ID + " AND r.deleted = 0"
                + " AND r.submitted_at >= ? AND r.submitted_at < ?");
        args.add(start);
        args.add(end.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "t.owner_id", "t.dept_id");
        Map<String, Object> row = jdbcTemplate.queryForMap(sql.toString(), args.toArray());
        CsatSummary summary = new CsatSummary();
        summary.setAvgScore(decimal(row.get("avg_score")));
        summary.setLowCount(((Number) row.getOrDefault("low_count", 0)).longValue());
        summary.setResponseCount(((Number) row.getOrDefault("response_count", 0)).longValue());
        return summary;
    }

    /**
     * 查询月度销售目标合计。
     *
     * @param period 周期（yyyy-MM）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 目标金额
     */
    @Override
    public BigDecimal monthlyTargetAmount(String period, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(t.target_amount), 0) FROM crm_sales_target t"
                + " WHERE t.tenant_id = " + TENANT_ID + " AND t.deleted = 0"
                + " AND t.period_type = 'MONTH' AND t.period = ?");
        args.add(period);
        appendScope(sql, args, ownerId, deptId, "t.owner_id", "t.dept_id");
        return amount(sql.toString(), args);
    }

    /**
     * 查询概率加权管道金额。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 加权金额
     */
    @Override
    public BigDecimal weightedPipelineAmount(LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(o.amount * COALESCE(o.probability, 0) / 100), 0)"
                + " FROM crm_opportunity o WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0"
                + " AND o.stage NOT IN ('WON', 'LOST')");
        appendScope(sql, args, ownerId, deptId, "o.owner_id", "o.dept_id");
        return amount(sql.toString(), args);
    }

    /**
     * 查询风险商机数量。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 风险商机数
     */
    @Override
    public long riskOpportunityCount(LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM crm_opportunity o"
                + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0"
                + " AND o.stage NOT IN ('WON', 'LOST') AND o.expected_close_date < ?");
        args.add(statDate);
        appendScope(sql, args, ownerId, deptId, "o.owner_id", "o.dept_id");
        return count(sql.toString(), args);
    }

    /**
     * 查询未关闭商机数量。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 未关闭商机数
     */
    @Override
    public long openOpportunityCount(LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM crm_opportunity o"
                + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0"
                + " AND o.stage NOT IN ('WON', 'LOST')");
        appendScope(sql, args, ownerId, deptId, "o.owner_id", "o.dept_id");
        return count(sql.toString(), args);
    }

    /**
     * 查询未关闭商机金额。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 未关闭商机金额
     */
    @Override
    public BigDecimal openOpportunityAmount(LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(o.amount), 0) FROM crm_opportunity o"
                + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0"
                + " AND o.stage NOT IN ('WON', 'LOST')");
        appendScope(sql, args, ownerId, deptId, "o.owner_id", "o.dept_id");
        return amount(sql.toString(), args);
    }

    /**
     * 查询长期未跟进的重点客户。
     *
     * @param thresholdDays 未跟进天数阈值
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 候选行动项
     */
    @Override
    public List<ActionCandidate> idleCustomers(int thresholdDays, LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT c.id AS customer_id, c.name AS customer_name,"
                + " c.level AS level, c.owner_id AS owner_id, c.dept_id AS dept_id,"
                + " TIMESTAMPDIFF(DAY, COALESCE(MAX(f.create_time), c.create_time), ?) AS idle_days"
                + " FROM crm_customer c"
                + " LEFT JOIN crm_follow_up f ON f.customer_id = c.id"
                + " AND f.tenant_id = " + TENANT_ID + " AND f.deleted = 0"
                + " WHERE c.tenant_id = " + TENANT_ID + " AND c.deleted = 0 AND c.status = 'ACTIVE'"
                + " AND c.level IN ('KEY', 'A', 'B')");
        args.add(statDate);
        appendScope(sql, args, ownerId, deptId, "c.owner_id", "c.dept_id");
        sql.append(" GROUP BY c.id, c.name, c.level, c.owner_id, c.dept_id, c.create_time"
                + " HAVING COALESCE(MAX(f.create_time), c.create_time) < ?"
                + " ORDER BY idle_days DESC LIMIT ").append(DEFAULT_LIMIT);
        args.add(statDate.minusDays(thresholdDays));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            ActionCandidate candidate = new ActionCandidate();
            candidate.setActionType(ActionType.CUSTOMER);
            candidate.setRefId(rs.getLong("customer_id"));
            candidate.setCustomerId(rs.getLong("customer_id"));
            candidate.setCustomerName(rs.getString("customer_name"));
            candidate.setTitle(rs.getString("customer_name"));
            candidate.setOverdueDays(rs.getInt("idle_days"));
            candidate.setMetricValue(BigDecimal.valueOf(rs.getInt("idle_days")));
            candidate.setPriority("KEY".equals(rs.getString("level")) || "A".equals(rs.getString("level"))
                    ? "P1" : "P2");
            candidate.setOwnerId(rs.getLong("owner_id"));
            candidate.setDeptId(rs.getLong("dept_id"));
            candidate.setDueDate(statDate.plusDays(7));
            return candidate;
        }, args.toArray());
    }

    /**
     * 查询预计关闭逾期的商机。
     *
     * @param graceDays 宽限天数
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 候选行动项
     */
    @Override
    public List<ActionCandidate> overdueOpportunities(int graceDays, LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT o.id AS opportunity_id, o.name AS title,"
                + " o.customer_id AS customer_id, c.name AS customer_name,"
                + " o.owner_id AS owner_id, o.dept_id AS dept_id,"
                + " TIMESTAMPDIFF(DAY, o.expected_close_date, ?) AS overdue_days"
                + " FROM crm_opportunity o"
                + " LEFT JOIN crm_customer c ON c.id = o.customer_id"
                + " AND c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0"
                + " AND o.stage NOT IN ('WON', 'LOST')"
                + " AND o.expected_close_date IS NOT NULL AND o.expected_close_date < ?");
        args.add(statDate);
        args.add(statDate.minusDays(graceDays));
        appendScope(sql, args, ownerId, deptId, "o.owner_id", "o.dept_id");
        sql.append(" ORDER BY overdue_days DESC LIMIT ").append(DEFAULT_LIMIT);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            ActionCandidate candidate = new ActionCandidate();
            candidate.setActionType(ActionType.OPPORTUNITY);
            candidate.setRefId(rs.getLong("opportunity_id"));
            candidate.setCustomerId(rs.getLong("customer_id"));
            candidate.setCustomerName(rs.getString("customer_name"));
            candidate.setTitle(rs.getString("title"));
            candidate.setOverdueDays(rs.getInt("overdue_days"));
            candidate.setPriority(rs.getInt("overdue_days") > 30 ? "P1" : "P2");
            candidate.setOwnerId(rs.getLong("owner_id"));
            candidate.setDeptId(rs.getLong("dept_id"));
            candidate.setDueDate(statDate.plusDays(3));
            return candidate;
        }, args.toArray());
    }

    /**
     * 查询月度目标缺口。
     *
     * @param period 周期（yyyy-MM）
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 候选行动项
     */
    @Override
    public List<ActionCandidate> targetGaps(String period, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT t.id AS target_id, t.owner_id AS owner_id,"
                + " t.dept_id AS dept_id,"
                + " (t.target_amount - COALESCE((SELECT SUM(o.amount) FROM crm_opportunity o"
                + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0 AND o.stage = 'WON'"
                + " AND o.owner_id = t.owner_id AND DATE_FORMAT(o.closed_at, '%Y-%m') = t.period), 0))"
                + " AS gap_amount"
                + " FROM crm_sales_target t"
                + " WHERE t.tenant_id = " + TENANT_ID + " AND t.deleted = 0"
                + " AND t.period_type = 'MONTH' AND t.period = ?");
        args.add(period);
        appendScope(sql, args, ownerId, deptId, "t.owner_id", "t.dept_id");
        sql.append(" HAVING gap_amount > 0 ORDER BY gap_amount DESC LIMIT ").append(DEFAULT_LIMIT);
        YearMonth month = YearMonth.parse(period, DateTimeFormatter.ofPattern("yyyy-MM"));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            ActionCandidate candidate = new ActionCandidate();
            candidate.setActionType(ActionType.TARGET);
            candidate.setRefId(rs.getLong("target_id"));
            candidate.setTitle("月度销售目标");
            candidate.setMetricValue(rs.getBigDecimal("gap_amount"));
            candidate.setPriority("P2");
            candidate.setOwnerId(rs.getLong("owner_id"));
            candidate.setDeptId(rs.getLong("dept_id"));
            candidate.setDueDate(month.atEndOfMonth());
            return candidate;
        }, args.toArray());
    }

    /**
     * 查询即将到期的合同。
     *
     * @param days 到期窗口天数
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 候选行动项
     */
    @Override
    public List<ActionCandidate> expiringContracts(int days, LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ct.id AS contract_id,"
                + " COALESCE(ct.title, ct.contract_no) AS title, ct.contract_no AS contract_no,"
                + " ct.customer_id AS customer_id, c.name AS customer_name,"
                + " ct.owner_id AS owner_id, ct.dept_id AS dept_id, ct.expire_date AS expire_date,"
                + " TIMESTAMPDIFF(DAY, ?, ct.expire_date) AS remaining_days"
                + " FROM crm_contract ct"
                + " LEFT JOIN crm_customer c ON c.id = ct.customer_id"
                + " AND c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                + " WHERE ct.tenant_id = " + TENANT_ID + " AND ct.deleted = 0"
                + " AND ct.expire_date IS NOT NULL AND ct.expire_date >= ? AND ct.expire_date <= ?");
        args.add(statDate);
        args.add(statDate);
        args.add(statDate.plusDays(days));
        appendScope(sql, args, ownerId, deptId, "ct.owner_id", "ct.dept_id");
        sql.append(" ORDER BY ct.expire_date ASC LIMIT ").append(DEFAULT_LIMIT);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            ActionCandidate candidate = new ActionCandidate();
            candidate.setActionType(ActionType.CONTRACT);
            candidate.setRefId(rs.getLong("contract_id"));
            candidate.setCustomerId(rs.getLong("customer_id"));
            candidate.setCustomerName(rs.getString("customer_name"));
            candidate.setTitle(rs.getString("title"));
            candidate.setOverdueDays(rs.getInt("remaining_days"));
            candidate.setPriority(rs.getInt("remaining_days") <= 30 ? "P1" : "P2");
            candidate.setOwnerId(rs.getLong("owner_id"));
            candidate.setDeptId(rs.getLong("dept_id"));
            LocalDate expireDate = rs.getDate("expire_date").toLocalDate();
            LocalDate dueDate = expireDate.minusDays(7);
            candidate.setDueDate(dueDate.isBefore(statDate) ? statDate : dueDate);
            return candidate;
        }, args.toArray());
    }

    /**
     * 查询逾期应收客户。
     *
     * @param amountThreshold 逾期金额阈值
     * @param statDate 统计日
     * @param ownerId 负责人范围，可空
     * @param deptId 部门范围，可空
     * @return 候选行动项
     */
    @Override
    public List<ActionCandidate> overdueReceivables(BigDecimal amountThreshold, LocalDate statDate,
                                                    Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT a.customer_id AS customer_id,"
                + " COALESCE(MAX(a.customer_name), MAX(c.name)) AS customer_name,"
                + " MAX(a.owner_id) AS owner_id, MAX(a.dept_id) AS dept_id,"
                + " SUM(a.overdue_1_30 + a.overdue_31_60 + a.overdue_61_90 + a.overdue_over_90)"
                + " AS overdue_amount"
                + " FROM crm_ar_snapshot a"
                + " LEFT JOIN crm_customer c ON c.id = a.customer_id"
                + " AND c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                + " WHERE a.tenant_id = " + TENANT_ID + " AND a.deleted = 0"
                + " AND a.snapshot_date = (SELECT MAX(s.snapshot_date) FROM crm_ar_snapshot s"
                + " WHERE s.tenant_id = " + TENANT_ID + " AND s.deleted = 0 AND s.snapshot_date <= ?)");
        args.add(statDate);
        appendScope(sql, args, ownerId, deptId, "a.owner_id", "a.dept_id");
        sql.append(" GROUP BY a.customer_id HAVING overdue_amount > ?"
                + " ORDER BY overdue_amount DESC LIMIT ").append(DEFAULT_LIMIT);
        args.add(amountThreshold == null ? BigDecimal.ZERO : amountThreshold);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            ActionCandidate candidate = new ActionCandidate();
            candidate.setActionType(ActionType.INVOICE);
            candidate.setRefId(rs.getLong("customer_id"));
            candidate.setCustomerId(rs.getLong("customer_id"));
            candidate.setCustomerName(rs.getString("customer_name"));
            candidate.setTitle(rs.getString("customer_name"));
            BigDecimal overdue = rs.getBigDecimal("overdue_amount");
            candidate.setMetricValue(overdue);
            candidate.setPriority(overdue != null && overdue.compareTo(BigDecimal.valueOf(1000000)) >= 0
                    ? "P1" : "P2");
            candidate.setOwnerId(rs.getLong("owner_id"));
            candidate.setDeptId(rs.getLong("dept_id"));
            candidate.setDueDate(statDate.plusDays(3));
            return candidate;
        }, args.toArray());
    }

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
    @Override
    public List<ActionCandidate> lowCsatTickets(int maxScore, LocalDate start, LocalDate end,
                                                Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT r.ticket_id AS ticket_id, r.score AS score,"
                + " t.title AS title, t.customer_id AS customer_id, t.customer_name AS customer_name,"
                + " t.owner_id AS owner_id, t.dept_id AS dept_id"
                + " FROM crm_satisfaction_response r"
                + " JOIN crm_ticket t ON t.id = r.ticket_id"
                + " AND t.tenant_id = " + TENANT_ID + " AND t.deleted = 0"
                + " WHERE r.tenant_id = " + TENANT_ID + " AND r.deleted = 0"
                + " AND r.low_score = 1 AND r.score <= ?"
                + " AND r.submitted_at >= ? AND r.submitted_at < ?");
        args.add(maxScore);
        args.add(start);
        args.add(end.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "t.owner_id", "t.dept_id");
        sql.append(" ORDER BY r.submitted_at DESC LIMIT ").append(DEFAULT_LIMIT);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            ActionCandidate candidate = new ActionCandidate();
            candidate.setActionType(ActionType.TICKET);
            candidate.setRefId(rs.getLong("ticket_id"));
            candidate.setCustomerId(rs.getLong("customer_id"));
            candidate.setCustomerName(rs.getString("customer_name"));
            candidate.setTitle(rs.getString("title"));
            candidate.setMetricValue(BigDecimal.valueOf(rs.getInt("score")));
            candidate.setPriority("P1");
            candidate.setOwnerId(rs.getLong("owner_id"));
            candidate.setDeptId(rs.getLong("dept_id"));
            candidate.setDueDate(end.plusDays(2));
            return candidate;
        }, args.toArray());
    }

    /**
     * 读取导出数据。
     *
     * @param type 导出类型
     * @param filtersJson 筛选条件 JSON
     * @param maxRows 单次最大行数
     * @return 导出数据表
     */
    @Override
    public ExportTable exportTable(ExportType type, String filtersJson, int maxRows) {
        int limit = (maxRows <= 0 ? DEFAULT_LIMIT : maxRows) + 1;
        return switch (type) {
            case CUSTOMER -> customerExport(limit);
            case OPPORTUNITY -> opportunityExport(limit);
            case ORDER -> orderExport(limit);
            case COLLECTION -> collectionExport(limit);
            case ACTION -> actionExport(limit);
            default -> buildTable(List.of(), List.of(), List.of());
        };
    }

    /**
     * 客户导出数据集。
     *
     * @param limit 查询上限
     * @return 导出数据表
     */
    private ExportTable customerExport(int limit) {
        List<String> headers = List.of("客户编码", "客户名称", "等级", "行业", "来源", "省", "市",
                "手机号", "邮箱", "创建时间");
        List<String> keys = List.of("customer_code", "name", "level", "industry", "source",
                "province", "city", "phone", "email", "create_time");
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT c.customer_code AS customer_code, c.name AS name,"
                        + " c.level AS level, c.industry AS industry, c.source AS source,"
                        + " a.province AS province, a.city AS city, ct.phone AS phone, ct.email AS email,"
                        + " c.create_time AS create_time"
                        + " FROM crm_customer c"
                        + " LEFT JOIN crm_contact ct ON ct.customer_id = c.id"
                        + " AND ct.tenant_id = " + TENANT_ID + " AND ct.deleted = 0 AND ct.is_primary = 1"
                        + " LEFT JOIN crm_customer_address a ON a.customer_id = c.id"
                        + " AND a.tenant_id = " + TENANT_ID + " AND a.deleted = 0 AND a.is_default_shipping = 1"
                        + " WHERE c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                        + " ORDER BY c.id DESC LIMIT " + limit);
        return buildTable(headers, keys, records);
    }

    /**
     * 商机导出数据集。
     *
     * @param limit 查询上限
     * @return 导出数据表
     */
    private ExportTable opportunityExport(int limit) {
        List<String> headers = List.of("商机名称", "客户名称", "阶段", "金额", "预计关闭日期", "负责人", "创建时间");
        List<String> keys = List.of("name", "customer_name", "stage", "amount", "expected_close_date",
                "owner_id", "create_time");
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT o.name AS name, c.name AS customer_name,"
                        + " o.stage AS stage, o.amount AS amount,"
                        + " o.expected_close_date AS expected_close_date, o.owner_id AS owner_id,"
                        + " o.create_time AS create_time"
                        + " FROM crm_opportunity o"
                        + " LEFT JOIN crm_customer c ON c.id = o.customer_id"
                        + " AND c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                        + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0"
                        + " ORDER BY o.create_time DESC LIMIT " + limit);
        return buildTable(headers, keys, records);
    }

    /**
     * 订单导出数据集。
     *
     * @param limit 查询上限
     * @return 导出数据表
     */
    private ExportTable orderExport(int limit) {
        List<String> headers = List.of("订单编号", "订单标题", "客户名称", "订单金额", "状态", "创建时间");
        List<String> keys = List.of("order_no", "title", "customer_name", "total_amount", "status", "create_time");
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT o.order_no AS order_no, o.title AS title,"
                        + " c.name AS customer_name, o.total_amount AS total_amount, o.status AS status,"
                        + " o.create_time AS create_time"
                        + " FROM crm_sales_order o"
                        + " LEFT JOIN crm_customer c ON c.id = o.customer_id"
                        + " AND c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                        + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0"
                        + " ORDER BY o.create_time DESC LIMIT " + limit);
        return buildTable(headers, keys, records);
    }

    /**
     * 回款导出数据集。
     *
     * @param limit 查询上限
     * @return 导出数据表
     */
    private ExportTable collectionExport(int limit) {
        List<String> headers = List.of("合同编号", "客户名称", "分期", "计划金额", "计划日期", "状态",
                "承诺日期", "承诺金额");
        List<String> keys = List.of("contract_no", "customer_name", "installment_no", "planned_amount",
                "planned_date", "status", "promise_date", "promise_amount");
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT ct.contract_no AS contract_no,"
                        + " c.name AS customer_name, p.installment_no AS installment_no,"
                        + " p.planned_amount AS planned_amount, p.planned_date AS planned_date,"
                        + " p.status AS status, p.promise_date AS promise_date,"
                        + " p.promise_amount AS promise_amount"
                        + " FROM crm_collection_plan p"
                        + " LEFT JOIN crm_contract ct ON ct.id = p.contract_id"
                        + " AND ct.tenant_id = " + TENANT_ID + " AND ct.deleted = 0"
                        + " LEFT JOIN crm_customer c ON c.id = p.customer_id"
                        + " AND c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                        + " WHERE p.tenant_id = " + TENANT_ID + " AND p.deleted = 0"
                        + " ORDER BY p.planned_date DESC LIMIT " + limit);
        return buildTable(headers, keys, records);
    }

    /**
     * 行动项导出数据集。
     *
     * @param limit 查询上限
     * @return 导出数据表
     */
    private ExportTable actionExport(int limit) {
        List<String> headers = List.of("规则编码", "关联类型", "关联ID", "客户名称", "标题", "优先级", "状态",
                "负责人", "建议完成日期");
        List<String> keys = List.of("rule_code", "ref_type", "ref_id", "customer_name", "title", "priority",
                "status", "owner_id", "due_date");
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT a.rule_code AS rule_code,"
                        + " a.ref_type AS ref_type, a.ref_id AS ref_id, a.customer_name AS customer_name,"
                        + " a.title AS title, a.priority AS priority, a.status AS status,"
                        + " a.owner_id AS owner_id, a.due_date AS due_date"
                        + " FROM crm_action_item a"
                        + " WHERE a.tenant_id = " + TENANT_ID + " AND a.deleted = 0"
                        + " ORDER BY a.create_time DESC LIMIT " + limit);
        return buildTable(headers, keys, records);
    }

    /**
     * 组装导出数据表。
     *
     * @param headers 表头
     * @param keys 字段键
     * @param records 记录
     * @return 导出数据表
     */
    private ExportTable buildTable(List<String> headers, List<String> keys,
                                   List<Map<String, Object>> records) {
        ExportTable table = new ExportTable();
        table.setHeaders(headers);
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> record : records) {
            List<String> row = new ArrayList<>();
            for (String key : keys) {
                row.add(cell(record.get(key)));
            }
            rows.add(row);
        }
        table.setRows(rows);
        table.setRowCount(records.size());
        return table;
    }

    /**
     * 二维分布统计。
     *
     * @param alias 别名
     * @param column 列表达式
     * @param statDate 统计日
     * @param ownerId 负责人范围
     * @param deptId 部门范围
     * @return 分布
     */
    private Map<String, Long> distribution(String alias, String column, LocalDate statDate,
                                           Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(" + column + ", '未填写') AS " + alias + ","
                + " COUNT(*) AS cnt FROM crm_customer c"
                + " WHERE c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                + " AND c.create_time >= ? AND c.create_time < ?");
        args.add(statDate.minusDays(30));
        args.add(statDate.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "c.owner_id", "c.dept_id");
        sql.append(" GROUP BY " + column + " ORDER BY cnt DESC");
        return mapCounts(jdbcTemplate.queryForList(sql.toString(), args.toArray()), alias, "cnt");
    }

    /**
     * 区域分布。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围
     * @param deptId 部门范围
     * @return 分布
     */
    private Map<String, Long> regionDistribution(LocalDate statDate, Long ownerId, Long deptId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COALESCE(a.province, '未填写') AS region,"
                + " COUNT(*) AS cnt FROM crm_customer c"
                + " LEFT JOIN crm_customer_address a ON a.customer_id = c.id"
                + " AND a.tenant_id = " + TENANT_ID + " AND a.deleted = 0 AND a.is_default_shipping = 1"
                + " WHERE c.tenant_id = " + TENANT_ID + " AND c.deleted = 0"
                + " AND c.create_time >= ? AND c.create_time < ?");
        args.add(statDate.minusDays(30));
        args.add(statDate.plusDays(1));
        appendScope(sql, args, ownerId, deptId, "c.owner_id", "c.dept_id");
        sql.append(" GROUP BY COALESCE(a.province, '未填写') ORDER BY cnt DESC");
        return mapCounts(jdbcTemplate.queryForList(sql.toString(), args.toArray()), "region", "cnt");
    }

    /**
     * 前 5 大客户赢单集中度。
     *
     * @param statDate 统计日
     * @param ownerId 负责人范围
     * @param deptId 部门范围
     * @return 百分比
     */
    private BigDecimal top5Concentration(LocalDate statDate, Long ownerId, Long deptId) {
        LocalDate start = statDate.minusDays(90);
        List<Object> topArgs = new ArrayList<>();
        StringBuilder topSql = new StringBuilder("SELECT COALESCE(SUM(t.amount), 0) FROM ("
                + " SELECT o.customer_id, SUM(o.amount) AS amount FROM crm_opportunity o"
                + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0 AND o.stage = 'WON'"
                + " AND o.closed_at >= ? AND o.closed_at < ?");
        topArgs.add(start);
        topArgs.add(statDate.plusDays(1));
        appendScope(topSql, topArgs, ownerId, deptId, "o.owner_id", "o.dept_id");
        topSql.append(" GROUP BY o.customer_id ORDER BY amount DESC LIMIT 5) t");
        BigDecimal top = amount(topSql.toString(), topArgs);

        List<Object> totalArgs = new ArrayList<>();
        StringBuilder totalSql = new StringBuilder("SELECT COALESCE(SUM(o.amount), 0) FROM crm_opportunity o"
                + " WHERE o.tenant_id = " + TENANT_ID + " AND o.deleted = 0 AND o.stage = 'WON'"
                + " AND o.closed_at >= ? AND o.closed_at < ?");
        totalArgs.add(start);
        totalArgs.add(statDate.plusDays(1));
        appendScope(totalSql, totalArgs, ownerId, deptId, "o.owner_id", "o.dept_id");
        BigDecimal total = amount(totalSql.toString(), totalArgs);
        if (total.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return top.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    /**
     * 计算回款率。
     *
     * @param contractAmount 合同金额
     * @param remaining 未结余额
     * @return 百分比
     */
    private BigDecimal collectionRate(BigDecimal contractAmount, BigDecimal remaining) {
        BigDecimal contract = contractAmount == null ? BigDecimal.ZERO : contractAmount;
        BigDecimal unpaid = remaining == null ? BigDecimal.ZERO : remaining;
        if (contract.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal received = contract.subtract(unpaid).max(BigDecimal.ZERO);
        return received.multiply(BigDecimal.valueOf(100)).divide(contract, 2, RoundingMode.HALF_UP);
    }

    /**
     * 将查询结果转换为计数分布。
     *
     * @param rows 查询结果
     * @param labelKey 标签键
     * @param countKey 计数键
     * @return 分布
     */
    private Map<String, Long> mapCounts(List<Map<String, Object>> rows, String labelKey, String countKey) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String label = row.get(labelKey) == null ? "未填写" : row.get(labelKey).toString();
            Object value = row.get(countKey);
            result.put(label, value instanceof Number number ? number.longValue() : 0L);
        }
        return result;
    }

    /**
     * 单元格文本。
     *
     * @param value 值
     * @return 文本
     */
    private String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME_FORMAT);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(DATE_TIME_FORMAT);
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalTime time) {
            return time.toString();
        }
        return value.toString();
    }

    /**
     * 追加数据范围条件。
     *
     * @param sql SQL
     * @param args 参数
     * @param ownerId 负责人
     * @param deptId 部门
     * @param ownerColumn 负责人列
     * @param deptColumn 部门列
     */
    private void appendScope(StringBuilder sql, List<Object> args, Long ownerId, Long deptId,
                             String ownerColumn, String deptColumn) {
        if (ownerId != null) {
            sql.append(" AND ").append(ownerColumn).append(" = ?");
            args.add(ownerId);
        }
        if (deptId != null) {
            sql.append(" AND ").append(deptColumn).append(" = ?");
            args.add(deptId);
        }
    }

    /**
     * 查询金额。
     *
     * @param sql SQL
     * @param args 参数列表
     * @return 金额
     */
    private BigDecimal amount(String sql, List<Object> args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args.toArray());
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 查询数量。
     *
     * @param sql SQL
     * @param args 参数列表
     * @return 数量
     */
    private long count(String sql, List<Object> args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return value == null ? 0L : value;
    }

    /**
     * 转换为金额。
     *
     * @param value 值
     * @return 金额
     */
    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }
}

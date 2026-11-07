package com.bluewhale.crm.analytics.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.analytics.application.service.AnalyticsAppService;
import com.bluewhale.crm.analytics.domain.model.ActionCandidate;
import com.bluewhale.crm.analytics.domain.model.CollectionAnalysisData;
import com.bluewhale.crm.analytics.domain.model.CsatSummary;
import com.bluewhale.crm.analytics.domain.model.CustomerAnalysisData;
import com.bluewhale.crm.analytics.domain.model.DashboardData;
import com.bluewhale.crm.analytics.domain.model.DimensionType;
import com.bluewhale.crm.analytics.domain.model.FunnelStage;
import com.bluewhale.crm.analytics.domain.model.FunnelViewData;
import com.bluewhale.crm.analytics.domain.model.LeadMetrics;
import com.bluewhale.crm.analytics.domain.model.MetricCode;
import com.bluewhale.crm.analytics.domain.model.MetricSnapshot;
import com.bluewhale.crm.analytics.domain.model.MetricSnapshotQuery;
import com.bluewhale.crm.analytics.domain.model.RankingRow;
import com.bluewhale.crm.analytics.domain.model.StageMetric;
import com.bluewhale.crm.analytics.domain.port.AnalyticsSourcePort;
import com.bluewhale.crm.analytics.domain.repository.ActionItemRepository;
import com.bluewhale.crm.analytics.domain.repository.MetricSnapshotRepository;
import com.bluewhale.crm.analytics.domain.service.MetricCalculator;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmAnalyticsProperties;
import com.bluewhale.crm.config.CrmProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据分析应用服务实现。
 *
 * <p>门禁：{@code whale.crm.demand-gate-passed} 与 {@code whale.crm.analytics.enabled}
 * 同时通过才可访问；非全量数据范围的操作人只统计本人数据。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AnalyticsAppServiceImpl implements AnalyticsAppService {

    /** 默认租户 */
    private static final long DEFAULT_TENANT_ID = 1L;

    /** 月份格式 */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 默认统计窗口天数 */
    private static final int DEFAULT_RANGE_DAYS = 30;

    /** 漏斗阶段顺序 */
    private static final List<String> STAGE_ORDER =
            List.of("LEAD", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "WON");

    /** 漏斗阶段中文名 */
    private static final Map<String, String> STAGE_LABELS = Map.of(
            "LEAD", "线索",
            "QUALIFIED", "资格验证",
            "PROPOSAL", "方案报价",
            "NEGOTIATION", "商务谈判",
            "WON", "赢单");

    private final AnalyticsSourcePort analyticsSourcePort;
    private final MetricSnapshotRepository metricSnapshotRepository;
    private final ActionItemRepository actionItemRepository;
    private final CrmProperties crmProperties;
    private final CrmAnalyticsProperties analyticsProperties;

    /** 指标计算器（纯 Java） */
    private final MetricCalculator metricCalculator = new MetricCalculator();

    /**
     * 销售看板。
     *
     * @param actor 操作人
     * @return 看板数据
     */
    @Override
    public DashboardData dashboard(CrmActor actor) {
        assertGate();
        Long ownerId = scopeOwner(actor);
        LocalDate today = LocalDate.now();
        LeadMetrics leadMetrics = analyticsSourcePort.leadMetrics(today, today, ownerId, null);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        BigDecimal weekWon = analyticsSourcePort.wonAmount(weekStart, today, ownerId, null);
        YearMonth month = YearMonth.from(today);
        BigDecimal monthTarget = analyticsSourcePort.monthlyTargetAmount(month.format(MONTH_FORMAT), ownerId, null);
        BigDecimal monthWon = analyticsSourcePort.wonAmount(month.atDay(1), today, ownerId, null);
        BigDecimal weightedPipeline = analyticsSourcePort.weightedPipelineAmount(today, ownerId, null);
        BigDecimal gap = monthTarget.subtract(monthWon).subtract(weightedPipeline);
        if (gap.signum() < 0) {
            gap = BigDecimal.ZERO;
        }
        DashboardData data = new DashboardData();
        data.setStatDate(today);
        data.setTodayNewLeads(leadMetrics.getNewCount());
        data.setWeekWonAmount(weekWon);
        data.setMonthTargetAmount(monthTarget);
        data.setMonthWonAmount(monthWon);
        data.setTargetCompletionRate(metricCalculator.targetCompletion(monthWon, monthTarget));
        data.setWeightedPipelineAmount(weightedPipeline);
        data.setForecastGap(gap);
        data.setRiskOpportunityCount(analyticsSourcePort.riskOpportunityCount(today, ownerId, null));
        data.setOpenActionCount(actionItemRepository.countOpen());
        return data;
    }

    /**
     * 漏斗分析。
     *
     * @param startDate 起始日，可空
     * @param endDate 结束日，可空
     * @param actor 操作人
     * @return 漏斗数据
     */
    @Override
    public FunnelViewData funnel(LocalDate startDate, LocalDate endDate, CrmActor actor) {
        assertGate();
        DateRange range = resolveRange(startDate, endDate);
        Long ownerId = scopeOwner(actor);
        List<StageMetric> metrics = analyticsSourcePort.opportunityStageMetrics(
                range.start(), range.end(), ownerId, null);
        Map<String, StageMetric> metricMap = new HashMap<>();
        StageMetric lostMetric = null;
        for (StageMetric metric : metrics) {
            String stage = metric.getStage() == null ? "" : metric.getStage().toUpperCase(Locale.ROOT);
            if ("LOST".equals(stage)) {
                lostMetric = metric;
            } else {
                metricMap.put(stage, metric);
            }
        }
        List<FunnelStage> stages = new ArrayList<>();
        long previousCount = 0L;
        long firstCount = 0L;
        BigDecimal firstAmount = BigDecimal.ZERO;
        for (int index = 0; index < STAGE_ORDER.size(); index++) {
            String stageCode = STAGE_ORDER.get(index);
            StageMetric metric = metricMap.get(stageCode);
            FunnelStage stage = new FunnelStage();
            stage.setStage(stageCode);
            stage.setStageLabel(STAGE_LABELS.getOrDefault(stageCode, stageCode));
            stage.setCount(metric == null ? 0L : metric.getCount());
            stage.setAmount(metric == null || metric.getAmount() == null ? BigDecimal.ZERO : metric.getAmount());
            stage.setAvgStayDays(metric == null || metric.getAvgStayDays() == null
                    ? BigDecimal.ZERO : metric.getAvgStayDays());
            if (index == 0) {
                stage.setConversionRate(stage.getCount() == 0 ? BigDecimal.ZERO
                        : metricCalculator.conversionRate(stage.getCount(), stage.getCount()));
                firstCount = stage.getCount();
                firstAmount = stage.getAmount();
            } else {
                stage.setConversionRate(metricCalculator.conversionRate(stage.getCount(), previousCount));
            }
            previousCount = stage.getCount();
            stages.add(stage);
        }
        FunnelViewData data = new FunnelViewData();
        data.setStartDate(range.start());
        data.setEndDate(range.end());
        data.setStages(stages);
        data.setLostCount(lostMetric == null ? 0L : lostMetric.getCount());
        data.setLostAmount(lostMetric == null || lostMetric.getAmount() == null
                ? BigDecimal.ZERO : lostMetric.getAmount());
        data.setTotalCount(firstCount);
        data.setTotalAmount(firstAmount);
        return data;
    }

    /**
     * 客户分析。
     *
     * @param statDate 统计日，可空
     * @param actor 操作人
     * @return 客户分析数据
     */
    @Override
    public CustomerAnalysisData customerAnalysis(LocalDate statDate, CrmActor actor) {
        assertGate();
        LocalDate date = statDate == null ? LocalDate.now() : statDate;
        return analyticsSourcePort.customerAnalysis(date, scopeOwner(actor), null);
    }

    /**
     * 销售排行。
     *
     * @param startDate 起始日，可空
     * @param endDate 结束日，可空
     * @param actor 操作人
     * @return 排行行
     */
    @Override
    public List<RankingRow> ranking(LocalDate startDate, LocalDate endDate, CrmActor actor) {
        assertGate();
        DateRange range = resolveRange(startDate, endDate);
        List<RankingRow> rows = analyticsSourcePort.ranking(range.start(), range.end(), scopeOwner(actor), null);
        for (int index = 0; index < rows.size(); index++) {
            rows.get(index).setRank(index + 1);
        }
        return rows;
    }

    /**
     * 回款分析。
     *
     * @param statDate 统计日，可空
     * @param actor 操作人
     * @return 回款分析数据
     */
    @Override
    public CollectionAnalysisData collectionAnalysis(LocalDate statDate, CrmActor actor) {
        assertGate();
        LocalDate date = statDate == null ? LocalDate.now() : statDate;
        Long ownerId = scopeOwner(actor);
        List<ActionCandidate> keyCustomers = analyticsSourcePort.overdueReceivables(
                BigDecimal.ZERO, date, ownerId, null);
        CollectionAnalysisData data = new CollectionAnalysisData();
        data.setStatDate(date);
        data.setReceivedAmount(analyticsSourcePort.collectionReceivedAmount(date, ownerId, null));
        data.setOverdueAmount(analyticsSourcePort.arOverdueAmount(date, ownerId, null));
        data.setOverdueCustomerCount(keyCustomers.size());
        data.setAgingDistribution(analyticsSourcePort.arAging(date, ownerId, null));
        data.setKeyOverdueCustomers(keyCustomers);
        return data;
    }

    /**
     * 生成指定日期的指标快照。
     *
     * @param statDate 统计日，可空（默认昨日）
     * @return 生成条数
     */
    @Override
    public int generateSnapshots(LocalDate statDate) {
        assertGate();
        LocalDate date = statDate == null ? LocalDate.now().minusDays(1) : statDate;
        if (date.isAfter(LocalDate.now())) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "统计日期不能晚于今天");
        }
        LeadMetrics leadMetrics = analyticsSourcePort.leadMetrics(date, date, null, null);
        CustomerAnalysisData customerData = analyticsSourcePort.customerAnalysis(date, null, null);
        CsatSummary csatSummary = analyticsSourcePort.csatSummary(date.minusDays(30), date, null, null);
        Map<MetricCode, BigDecimal> values = new EnumMap<>(MetricCode.class);
        values.put(MetricCode.LEAD_NEW, BigDecimal.valueOf(leadMetrics.getNewCount()));
        values.put(MetricCode.LEAD_CONVERTED, BigDecimal.valueOf(leadMetrics.getConvertedCount()));
        values.put(MetricCode.OPPORTUNITY_OPEN_COUNT,
                BigDecimal.valueOf(analyticsSourcePort.openOpportunityCount(date, null, null)));
        values.put(MetricCode.OPPORTUNITY_OPEN_AMOUNT,
                analyticsSourcePort.openOpportunityAmount(date, null, null));
        values.put(MetricCode.WON_AMOUNT, analyticsSourcePort.wonAmount(date, date, null, null));
        values.put(MetricCode.CONTRACT_AMOUNT, analyticsSourcePort.contractAmount(date, date, null, null));
        values.put(MetricCode.INVOICE_AMOUNT, analyticsSourcePort.invoiceAmount(date, date, null, null));
        values.put(MetricCode.COLLECTION_RECEIVED, analyticsSourcePort.collectionReceivedAmount(date, null, null));
        values.put(MetricCode.AR_OVERDUE_AMOUNT, analyticsSourcePort.arOverdueAmount(date, null, null));
        values.put(MetricCode.CUSTOMER_NEW,
                BigDecimal.valueOf(analyticsSourcePort.newCustomerCount(date, null, null)));
        values.put(MetricCode.CUSTOMER_ACTIVE, BigDecimal.valueOf(customerData.getActiveCount()));
        values.put(MetricCode.CSAT_AVG, csatSummary.getAvgScore());
        int count = 0;
        for (Map.Entry<MetricCode, BigDecimal> entry : values.entrySet()) {
            MetricSnapshot snapshot = new MetricSnapshot();
            snapshot.setTenantId(DEFAULT_TENANT_ID);
            snapshot.setMetricCode(entry.getKey());
            snapshot.setStatDate(date);
            snapshot.setDimensionType(DimensionType.TOTAL);
            snapshot.setDimensionId("0");
            snapshot.setMetricValue(entry.getValue() == null ? BigDecimal.ZERO : entry.getValue());
            snapshot.setMetricExtra("自动生成：TOTAL");
            metricSnapshotRepository.upsert(snapshot);
            count++;
        }
        return count;
    }

    /**
     * 分页查询指标快照。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    @Override
    public PageResult<MetricSnapshot> listSnapshots(MetricSnapshotQuery query, CrmActor actor) {
        assertGate();
        List<MetricSnapshot> records = metricSnapshotRepository.page(query);
        long total = metricSnapshotRepository.count(query);
        return PageResult.of(records, total, query.getCurrent(), query.getSize());
    }

    /**
     * 解析统计区间。
     *
     * @param startDate 起始日
     * @param endDate 结束日
     * @return 统计区间
     */
    private DateRange resolveRange(LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.minusDays(DEFAULT_RANGE_DAYS - 1) : startDate;
        if (start.isAfter(end)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "起始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) > analyticsProperties.getDashboardLookbackDays()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID,
                    "统计区间不能超过 " + analyticsProperties.getDashboardLookbackDays() + " 天");
        }
        return new DateRange(start, end);
    }

    /**
     * 解析数据范围负责人。
     *
     * @param actor 操作人
     * @return 全量返回 null，否则返回本人
     */
    private Long scopeOwner(CrmActor actor) {
        return actor.allDataScope() ? null : actor.requireUserId();
    }

    /**
     * 门禁校验。
     */
    private void assertGate() {
        if (!crmProperties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
        if (!analyticsProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED, "数据分析模块未启用");
        }
    }

    /**
     * 统计区间。
     *
     * @param start 起始日
     * @param end 结束日
     */
    private record DateRange(LocalDate start, LocalDate end) {
    }
}

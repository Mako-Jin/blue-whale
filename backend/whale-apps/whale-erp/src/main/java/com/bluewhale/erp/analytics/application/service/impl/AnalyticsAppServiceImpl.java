package com.bluewhale.erp.analytics.application.service.impl;

import com.bluewhale.erp.analytics.application.command.CockpitRefreshCommand;
import com.bluewhale.erp.analytics.application.service.AnalyticsAppService;
import com.bluewhale.erp.analytics.application.view.CockpitView;
import com.bluewhale.erp.analytics.application.view.CubeResultView;
import com.bluewhale.erp.analytics.application.view.CubeRowView;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;
import com.bluewhale.erp.analytics.domain.model.AnalyticsStatus;
import com.bluewhale.erp.analytics.domain.model.CockpitSnapshot;
import com.bluewhale.erp.analytics.domain.model.CostFact;
import com.bluewhale.erp.analytics.domain.model.SalesFact;
import com.bluewhale.erp.analytics.domain.repository.AlertRepository;
import com.bluewhale.erp.analytics.domain.repository.AnalyticsRepository;
import com.bluewhale.erp.common.ErpErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 驾驶舱与多维分析应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AnalyticsAppServiceImpl implements AnalyticsAppService {

    /** 期间格式 */
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final AnalyticsRepository analyticsRepository;

    private final AlertRepository alertRepository;

    @Override
    @Transactional
    public CockpitView getCockpit(String periodCode) {
        String period = resolvePeriod(periodCode);
        CockpitSnapshot snapshot = analyticsRepository.findActiveSnapshot(period)
                .orElseGet(() -> persistCurrent(period, defaultStatDate(period)));
        return toCockpitView(snapshot);
    }

    @Override
    @Transactional
    public CockpitView refreshCockpit(CockpitRefreshCommand command) {
        String period = resolvePeriod(command.getPeriodCode());
        LocalDate statDate = command.getStatDate() == null ? defaultStatDate(period) : command.getStatDate();
        if (!statDate.toString().replace("-", "").startsWith(period)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "统计日期必须落在统计期间内");
        }
        analyticsRepository.markPeriodStale(period);
        return toCockpitView(persist(period, statDate));
    }

    @Override
    @Transactional
    public void invalidateCockpit(String periodCode) {
        analyticsRepository.markPeriodStale(resolvePeriod(periodCode));
    }

    @Override
    public CubeResultView cube(AnalyticsQuery query) {
        String dimension = query.getDimensionType();
        String metric = query.getMetricType();
        validateDimension(dimension);
        validateMetric(metric);
        if (AnalyticsStatus.METRIC_COST.equals(metric)
                && AnalyticsStatus.DIMENSION_CUSTOMER.equals(dimension)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID,
                    "成本指标不支持按客户维度归集，请选择物料或仓库维度");
        }
        LocalDate from = query.getFromDate() == null ? LocalDate.now().withDayOfMonth(1) : query.getFromDate();
        LocalDate to = query.getToDate() == null ? LocalDate.now() : query.getToDate();
        if (from.isAfter(to)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "开始日期不能晚于结束日期");
        }
        CubeResultView result = new CubeResultView();
        result.setDimensionType(dimension);
        result.setMetricType(metric);
        result.setFromDate(from);
        result.setToDate(to);
        result.setRows(AnalyticsStatus.METRIC_COST.equals(metric)
                ? aggregateCost(dimension, from, to) : aggregateSales(dimension, metric, from, to));
        result.setTotalMetric(result.getRows().stream()
                .map(CubeRowView::getMetricValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setTotalOrderCount(result.getRows().stream()
                .map(CubeRowView::getOrderCount)
                .reduce(0, Integer::sum));
        if (result.getRows().isEmpty()) {
            result.setNote("当前区间无可分析事实数据");
        }
        return result;
    }

    /**
     * 按销售事实聚合。
     *
     * @param dimension 维度
     * @param metric 指标
     * @param from 日期起
     * @param to 日期止
     * @return 明细行
     */
    private List<CubeRowView> aggregateSales(String dimension, String metric, LocalDate from, LocalDate to) {
        List<SalesFact> facts = analyticsRepository.listSalesFacts(from, to);
        Map<String, Agg> groups = new HashMap<>();
        for (SalesFact fact : facts) {
            String key = dimensionKey(dimension, fact);
            Agg agg = groups.computeIfAbsent(key, unused -> new Agg(dimensionName(dimension, fact)));
            switch (metric) {
                case AnalyticsStatus.METRIC_REVENUE -> agg.metric = agg.metric.add(fact.shippedRevenue());
                case AnalyticsStatus.METRIC_QUANTITY -> agg.metric = agg.metric.add(zero(fact.getShippedQuantity()));
                case AnalyticsStatus.METRIC_ORDER_COUNT -> agg.docNos.add(fact.getDocNo());
                default -> throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "指标不支持销售事实");
            }
            if (fact.getDocNo() != null) {
                agg.docNos.add(fact.getDocNo());
            }
        }
        List<CubeRowView> rows = new ArrayList<>();
        groups.forEach((key, agg) -> rows.add(toRow(key, agg, metric)));
        rows.sort(Comparator.comparing(CubeRowView::getMetricValue).reversed());
        return rows;
    }

    /**
     * 按出库成本事实聚合。
     *
     * @param dimension 维度
     * @param from 日期起
     * @param to 日期止
     * @return 明细行
     */
    private List<CubeRowView> aggregateCost(String dimension, LocalDate from, LocalDate to) {
        List<CostFact> facts = analyticsRepository.listCostFacts(from, to);
        Map<String, Agg> groups = new HashMap<>();
        for (CostFact fact : facts) {
            String key = AnalyticsStatus.DIMENSION_ITEM.equals(dimension)
                    ? valueOrId(fact.getItemCode(), fact.getItemId())
                    : valueOrId(fact.getWarehouseId(), fact.getWarehouseId());
            String name = AnalyticsStatus.DIMENSION_ITEM.equals(dimension)
                    ? valueOrNull(fact.getItemName(), fact.getItemCode())
                    : "仓库 #" + fact.getWarehouseId();
            Agg agg = groups.computeIfAbsent(key, unused -> new Agg(name));
            agg.metric = agg.metric.add(zero(fact.getAmount()));
        }
        List<CubeRowView> rows = new ArrayList<>();
        groups.forEach((key, agg) -> rows.add(toRow(key, agg, AnalyticsStatus.METRIC_COST)));
        rows.sort(Comparator.comparing(CubeRowView::getMetricValue).reversed());
        return rows;
    }

    /**
     * 聚合结果转行视图。
     *
     * @param key 维度键
     * @param agg 聚合
     * @param metric 指标
     * @return 行
     */
    private CubeRowView toRow(String key, Agg agg, String metric) {
        CubeRowView row = new CubeRowView();
        row.setDimensionKey(key);
        row.setDimensionName(agg.name);
        row.setOrderCount(agg.docNos.size());
        row.setMetricValue(AnalyticsStatus.METRIC_ORDER_COUNT.equals(metric)
                ? BigDecimal.valueOf(agg.docNos.size()) : agg.metric);
        return row;
    }

    /**
     * 销售事实维度键。
     *
     * @param dimension 维度
     * @param fact 销售事实
     * @return 维度键
     */
    private String dimensionKey(String dimension, SalesFact fact) {
        return switch (dimension) {
            case AnalyticsStatus.DIMENSION_CUSTOMER -> valueOrId(fact.getCustomerName(), fact.getCustomerId());
            case AnalyticsStatus.DIMENSION_ITEM -> valueOrId(fact.getItemCode(), fact.getItemId());
            case AnalyticsStatus.DIMENSION_WAREHOUSE -> valueOrId(fact.getWarehouseId(), fact.getWarehouseId());
            default -> throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "维度不支持");
        };
    }

    /**
     * 销售事实维度名称。
     *
     * @param dimension 维度
     * @param fact 销售事实
     * @return 维度名称
     */
    private String dimensionName(String dimension, SalesFact fact) {
        return switch (dimension) {
            case AnalyticsStatus.DIMENSION_CUSTOMER -> valueOrNull(fact.getCustomerName(), "未指定客户");
            case AnalyticsStatus.DIMENSION_ITEM -> valueOrNull(fact.getItemName(), fact.getItemCode());
            case AnalyticsStatus.DIMENSION_WAREHOUSE -> "仓库 #" + fact.getWarehouseId();
            default -> throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "维度不支持");
        };
    }

    /**
     * 刷新并落库当前口径快照。
     *
     * @param period 统计期间
     * @param statDate 统计截止日期
     * @return 快照
     */
    private CockpitSnapshot persistCurrent(String period, LocalDate statDate) {
        analyticsRepository.markPeriodStale(period);
        return persist(period, statDate);
    }

    /**
     * 计算指标并落库快照。
     *
     * @param period 统计期间
     * @param statDate 统计截止日期
     * @return 快照
     */
    private CockpitSnapshot persist(String period, LocalDate statDate) {
        YearMonth yearMonth = YearMonth.parse(period, PERIOD_FORMATTER);
        LocalDate from = yearMonth.atDay(1);
        List<SalesFact> sales = analyticsRepository.listSalesFacts(from, statDate);
        List<CostFact> costs = analyticsRepository.listCostFacts(from, statDate);
        BigDecimal revenue = sales.stream().map(SalesFact::shippedRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = costs.stream().map(CostFact::getAmount).map(this::zero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int orderCount = (int) sales.stream()
                .map(SalesFact::getDocNo).filter(StringUtils::hasText).distinct().count();
        int alertCount = (int) alertRepository.countOpenEvents();
        CockpitSnapshot snapshot = analyticsRepository.findSnapshot(period, statDate)
                .orElseGet(() -> {
                    CockpitSnapshot created = new CockpitSnapshot();
                    created.setSnapshotNo(analyticsRepository.nextSnapshotNo(period));
                    created.setPeriodCode(period);
                    created.setStatDate(statDate);
                    return created;
                });
        snapshot.refresh(revenue, cost, orderCount, alertCount);
        if (snapshot.getId() == null) {
            snapshot.validateForCreate();
            analyticsRepository.insertSnapshot(snapshot);
        } else {
            analyticsRepository.updateSnapshot(snapshot);
        }
        return snapshot;
    }

    /**
     * 解析并校验统计期间。
     *
     * @param periodCode 期间，可空
     * @return 规范化期间
     */
    private String resolvePeriod(String periodCode) {
        String period = StringUtils.hasText(periodCode)
                ? periodCode : YearMonth.now().format(PERIOD_FORMATTER);
        if (!period.matches("\\d{6}")) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "统计期间必须为 yyyyMM");
        }
        return period;
    }

    /**
     * 期间缺省统计日：当期取当天，否则取期末。
     *
     * @param period 统计期间
     * @return 统计日
     */
    private LocalDate defaultStatDate(String period) {
        YearMonth yearMonth = YearMonth.parse(period, PERIOD_FORMATTER);
        LocalDate today = LocalDate.now();
        return YearMonth.from(today).equals(yearMonth) ? today : yearMonth.atEndOfMonth();
    }

    /**
     * 校验维度。
     *
     * @param dimension 维度
     */
    private void validateDimension(String dimension) {
        if (!Set.of(AnalyticsStatus.DIMENSION_CUSTOMER, AnalyticsStatus.DIMENSION_ITEM,
                AnalyticsStatus.DIMENSION_WAREHOUSE).contains(dimension)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID,
                    "维度仅支持 CUSTOMER / ITEM / WAREHOUSE");
        }
    }

    /**
     * 校验指标。
     *
     * @param metric 指标
     */
    private void validateMetric(String metric) {
        if (!Set.of(AnalyticsStatus.METRIC_REVENUE, AnalyticsStatus.METRIC_QUANTITY,
                AnalyticsStatus.METRIC_ORDER_COUNT, AnalyticsStatus.METRIC_COST).contains(metric)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID,
                    "指标仅支持 REVENUE / QUANTITY / ORDER_COUNT / COST");
        }
    }

    /**
     * 快照转视图。
     *
     * @param snapshot 快照
     * @return 视图
     */
    private CockpitView toCockpitView(CockpitSnapshot snapshot) {
        CockpitView view = new CockpitView();
        view.setSnapshotNo(snapshot.getSnapshotNo());
        view.setPeriodCode(snapshot.getPeriodCode());
        view.setStatDate(snapshot.getStatDate());
        view.setRevenueAmount(snapshot.getRevenueAmount());
        view.setCostAmount(snapshot.getCostAmount());
        view.setProfitAmount(snapshot.getProfitAmount());
        view.setOrderCount(snapshot.getOrderCount());
        view.setAlertCount(snapshot.getAlertCount());
        view.setStatus(snapshot.getStatus());
        view.setRefreshedAt(snapshot.getRefreshedAt());
        return view;
    }

    /**
     * 空值归零。
     *
     * @param value 金额或数量
     * @return 非空值
     */
    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 优先返回值，否则用 ID。
     *
     * @param value 值
     * @param id ID
     * @return 键
     */
    private String valueOrId(Object value, Object id) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return value == null ? String.valueOf(id) : String.valueOf(value);
    }

    /**
     * 优先返回值，否则用兜底值。
     *
     * @param value 值
     * @param fallback 兜底
     * @return 展示名
     */
    private String valueOrNull(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 聚合累加器。
     */
    private static final class Agg {

        /** 维度名称 */
        private String name;

        /** 指标值 */
        private BigDecimal metric = BigDecimal.ZERO;

        /** 订单号集合 */
        private final Set<String> docNos = new HashSet<>();

        /**
         * 构造累加器。
         *
         * @param name 维度名称
         */
        private Agg(String name) {
            this.name = name;
        }
    }
}

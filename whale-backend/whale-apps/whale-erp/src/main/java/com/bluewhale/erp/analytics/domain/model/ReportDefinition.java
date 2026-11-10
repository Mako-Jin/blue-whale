package com.bluewhale.erp.analytics.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.util.Set;

/**
 * 报表定义聚合。
 *
 * <p>不变量：报表编号租户内唯一；维度与指标必须在允许集合内；成本指标只支持物料/仓库维度
 * （销售事实中没有客户成本归属）。</p>
 *
 * @author BlueWhale
 */
@Data
public class ReportDefinition {

    /** 维度允许集合 */
    private static final Set<String> DIMENSIONS = Set.of(
            AnalyticsStatus.DIMENSION_CUSTOMER,
            AnalyticsStatus.DIMENSION_ITEM,
            AnalyticsStatus.DIMENSION_WAREHOUSE);

    /** 指标允许集合 */
    private static final Set<String> METRICS = Set.of(
            AnalyticsStatus.METRIC_REVENUE,
            AnalyticsStatus.METRIC_QUANTITY,
            AnalyticsStatus.METRIC_ORDER_COUNT,
            AnalyticsStatus.METRIC_COST);

    /** 主键 */
    private Long id;

    /** 报表编号 */
    private String reportNo;

    /** 报表名称 */
    private String reportName;

    /** 类型：SALES / STOCK / RECEIVABLE / CUSTOM */
    private String reportType;

    /** 维度 */
    private String dimensionType;

    /** 指标 */
    private String metricType;

    /** 过滤条件 JSON */
    private String filterJson;

    /** 状态 */
    private String status;

    /** 创建人 */
    private String owner;

    /** 备注 */
    private String remark;

    /**
     * 校验报表定义。
     */
    public void validateForCreate() {
        if (reportNo == null || reportNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "报表编号不能为空");
        }
        if (reportName == null || reportName.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "报表名称不能为空");
        }
        if (!DIMENSIONS.contains(dimensionType)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID,
                    "维度仅支持 CUSTOMER / ITEM / WAREHOUSE");
        }
        if (!METRICS.contains(metricType)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID,
                    "指标仅支持 REVENUE / QUANTITY / ORDER_COUNT / COST");
        }
        if (AnalyticsStatus.METRIC_COST.equals(metricType)
                && AnalyticsStatus.DIMENSION_CUSTOMER.equals(dimensionType)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID,
                    "成本指标不支持按客户维度归集，请选择物料或仓库维度");
        }
        reportType = reportType == null || reportType.isBlank() ? "CUSTOM" : reportType;
        status = status == null || status.isBlank() ? AnalyticsStatus.ENABLED : status;
    }
}

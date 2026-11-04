package com.bluewhale.erp.analytics.domain.model;

/**
 * 分析与报表中心状态与类型常量。
 *
 * @author BlueWhale
 */
public final class AnalyticsStatus {

    /** 快照有效 */
    public static final String SNAPSHOT_ACTIVE = "ACTIVE";

    /** 快照已失效 */
    public static final String SNAPSHOT_STALE = "STALE";

    /** 预警规则/报表启用 */
    public static final String ENABLED = "ENABLED";

    /** 停用 */
    public static final String DISABLED = "DISABLED";

    /** 预警事件待处理 */
    public static final String EVENT_OPEN = "OPEN";

    /** 预警事件已确认 */
    public static final String EVENT_ACKNOWLEDGED = "ACKNOWLEDGED";

    /** 预警事件已关闭 */
    public static final String EVENT_CLOSED = "CLOSED";

    /** 库存不足预警 */
    public static final String RULE_STOCK_LOW = "STOCK_LOW";

    /** 超期应收预警 */
    public static final String RULE_RECEIVABLE_OVERDUE = "RECEIVABLE_OVERDUE";

    /** 质量异常预警 */
    public static final String RULE_QUALITY_OPEN_NCR = "QUALITY_OPEN_NCR";

    /** 超预算预警（依赖预算模块数据） */
    public static final String RULE_BUDGET_EXCEEDED = "BUDGET_EXCEEDED";

    /** 按客户维度 */
    public static final String DIMENSION_CUSTOMER = "CUSTOMER";

    /** 按物料维度 */
    public static final String DIMENSION_ITEM = "ITEM";

    /** 按仓库维度 */
    public static final String DIMENSION_WAREHOUSE = "WAREHOUSE";

    /** 收入指标 */
    public static final String METRIC_REVENUE = "REVENUE";

    /** 数量指标 */
    public static final String METRIC_QUANTITY = "QUANTITY";

    /** 订单数指标 */
    public static final String METRIC_ORDER_COUNT = "ORDER_COUNT";

    /** 成本指标（仅物料/仓库维度） */
    public static final String METRIC_COST = "COST";

    /** 按日周期 */
    public static final String PERIOD_DAY = "DAY";

    /** 按周周期 */
    public static final String PERIOD_WEEK = "WEEK";

    /** 按月周期 */
    public static final String PERIOD_MONTH = "MONTH";

    /** 级别：提示 */
    public static final String LEVEL_INFO = "INFO";

    /** 级别：警告 */
    public static final String LEVEL_WARN = "WARN";

    /** 级别：严重 */
    public static final String LEVEL_CRITICAL = "CRITICAL";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private AnalyticsStatus() {
    }
}

package com.bluewhale.erp.analytics.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 经营驾驶舱快照聚合。
 *
 * <p>不变量：同租户同期间同统计日唯一；刷新时旧快照置为 {@code STALE}，保证缓存主动失效；
 * 利润 = 收入 − 成本。</p>
 *
 * @author BlueWhale
 */
@Data
public class CockpitSnapshot {

    /** 主键 */
    private Long id;

    /** 快照编号 */
    private String snapshotNo;

    /** 统计期间 yyyyMM */
    private String periodCode;

    /** 统计截止日期 */
    private LocalDate statDate;

    /** 收入金额 */
    private BigDecimal revenueAmount;

    /** 成本金额 */
    private BigDecimal costAmount;

    /** 利润金额 */
    private BigDecimal profitAmount;

    /** 订单数 */
    private Integer orderCount;

    /** 未关闭预警数 */
    private Integer alertCount;

    /** 状态：ACTIVE / STALE */
    private String status;

    /** 刷新时间 */
    private LocalDateTime refreshedAt;

    /** 备注 */
    private String remark;

    /**
     * 校验快照数据。
     */
    public void validateForCreate() {
        if (periodCode == null || periodCode.length() != 6) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "统计期间必须为 yyyyMM");
        }
        if (statDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "统计截止日期不能为空");
        }
        if (!statDate.toString().replace("-", "").startsWith(periodCode)) {
            throw ErpErrorCode.exception(ErpErrorCode.ANALYTICS_INVALID, "统计日期必须落在统计期间内");
        }
        revenueAmount = zeroIfNull(revenueAmount);
        costAmount = zeroIfNull(costAmount);
        alertCount = alertCount == null ? 0 : alertCount;
        orderCount = orderCount == null ? 0 : orderCount;
        profitAmount = revenueAmount.subtract(costAmount);
        refreshedAt = refreshedAt == null ? LocalDateTime.now() : refreshedAt;
        status = AnalyticsStatus.SNAPSHOT_ACTIVE;
    }

    /**
     * 用最新指标刷新快照。
     *
     * @param revenue 收入
     * @param cost 成本
     * @param orders 订单数
     * @param alerts 未关闭预警数
     */
    public void refresh(BigDecimal revenue, BigDecimal cost, int orders, int alerts) {
        revenueAmount = zeroIfNull(revenue);
        costAmount = zeroIfNull(cost);
        profitAmount = revenueAmount.subtract(costAmount);
        orderCount = orders;
        alertCount = alerts;
        refreshedAt = LocalDateTime.now();
        status = AnalyticsStatus.SNAPSHOT_ACTIVE;
    }

    /**
     * 主动失效快照。
     */
    public void invalidate() {
        status = AnalyticsStatus.SNAPSHOT_STALE;
    }

    /**
     * 空值归零。
     *
     * @param value 金额
     * @return 非空金额
     */
    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

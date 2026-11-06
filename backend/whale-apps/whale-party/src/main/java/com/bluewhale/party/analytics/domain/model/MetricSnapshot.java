package com.bluewhale.party.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业务统计快照（只存分子与分母）。
 *
 * <p><b>不落库比率</b>：比率是展示口径（保留位数、分母为 0 时的展示规则）会随时间调整，
 * 把比率写进历史等于把当时的展示规则固化下来，后续改口径就对不上账。
 * 看板与导出每次都从当前规则重新计算 {@link #rate()}。</p>
 *
 * <p>同一天同一指标同一范围只保留一行：重刷是覆盖写，不是追加。追加会让趋势图
 * 在同一天出现多个点，形成锯齿。</p>
 *
 * @author BlueWhale
 */
@Data
public class MetricSnapshot {

    /** 比率保留小数位 */
    public static final int RATE_SCALE = 2;

    /** 全量范围的 scope_id */
    public static final long ALL_SCOPE_ID = 0L;

    /** 主键 */
    private Long id;

    /** 统计日期 */
    private LocalDate statDate;

    /** 指标编码 */
    private String metricType;

    /** 统计范围 */
    private String scopeType;

    /** 范围 ID（ALL 为 0，BRANCH 为支部 ID） */
    private Long scopeId;

    /** 范围名称快照 */
    private String scopeName;

    /** 分子 */
    private BigDecimal numerator;

    /** 分母（纯计数指标为 0） */
    private BigDecimal denominator;

    /** 计算时刻 */
    private LocalDateTime computedAt;

    /**
     * 构造快照。
     *
     * @param statDate 统计日期
     * @param metricType 指标编码
     * @param scopeType 统计范围
     * @param scopeId 范围 ID
     * @param scopeName 范围名称
     * @param numerator 分子
     * @param denominator 分母
     * @return 快照
     */
    public static MetricSnapshot of(LocalDate statDate, String metricType, MetricScope scopeType,
                                    Long scopeId, String scopeName, BigDecimal numerator,
                                    BigDecimal denominator) {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setStatDate(statDate);
        snapshot.setMetricType(metricType);
        snapshot.setScopeType(scopeType.name());
        snapshot.setScopeId(scopeId == null ? ALL_SCOPE_ID : scopeId);
        snapshot.setScopeName(scopeName);
        snapshot.setNumerator(numerator == null ? BigDecimal.ZERO : numerator);
        snapshot.setDenominator(denominator == null ? BigDecimal.ZERO : denominator);
        snapshot.setComputedAt(LocalDateTime.now());
        return snapshot;
    }

    /**
     * 计算展示比率（百分数）。
     *
     * <p>分母为 0 返回 0.00：覆盖率的分子为 0 时语义是「有会员但无员工快照」，
     * 展示成 100% 或 -- 都会误导，统一显示 0 并由看板另给「快照未同步」提示。</p>
     *
     * @return 百分数（保留两位小数）
     */
    public BigDecimal rate() {
        if (denominator == null || denominator.signum() == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, RATE_SCALE, RoundingMode.HALF_UP);
    }
}

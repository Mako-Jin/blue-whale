package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 指标快照。
 *
 * <p>唯一键为 {@code tenantId + metricCode + statDate + dimensionType + dimensionId}；
 * 同一唯一键重跑时由仓储执行更新，不新增、不删除。</p>
 *
 * @author BlueWhale
 */
@Data
public class MetricSnapshot {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 指标编码 */
    private MetricCode metricCode;

    /** 统计日期 */
    private LocalDate statDate;

    /** 维度类型 */
    private DimensionType dimensionType;

    /** 维度值（TOTAL 为 0） */
    private String dimensionId;

    /** 指标值 */
    private BigDecimal metricValue;

    /** 扩展信息 */
    private String metricExtra;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

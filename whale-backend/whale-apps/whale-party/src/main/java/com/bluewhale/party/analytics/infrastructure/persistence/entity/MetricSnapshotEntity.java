package com.bluewhale.party.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统计快照实体，对应表 {@code pty_metric_snapshot}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_metric_snapshot")
public class MetricSnapshotEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 统计日期 */
    private LocalDate statDate;

    /** 指标编码 */
    private String metricType;

    /** 统计范围 */
    private String scopeType;

    /** 范围 ID */
    private Long scopeId;

    /** 范围名称快照 */
    private String scopeName;

    /** 分子 */
    private BigDecimal numerator;

    /** 分母 */
    private BigDecimal denominator;

    /** 计算时刻 */
    private LocalDateTime computedAt;
}

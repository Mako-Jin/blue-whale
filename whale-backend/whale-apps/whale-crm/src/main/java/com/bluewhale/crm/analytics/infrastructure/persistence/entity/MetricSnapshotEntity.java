package com.bluewhale.crm.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 指标快照实体，对应表 {@code crm_metric_snapshot}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_metric_snapshot")
public class MetricSnapshotEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 指标编码 */
    private String metricCode;

    /** 统计日期 */
    private LocalDate statDate;

    /** 维度类型 */
    private String dimensionType;

    /** 维度值 */
    private String dimensionId;

    /** 指标值 */
    private BigDecimal metricValue;

    /** 扩展信息 */
    private String metricExtra;
}

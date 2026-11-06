package com.bluewhale.lms.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习看板指标快照实体，对应表 {@code lr_metric_snapshot}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_metric_snapshot")
public class MetricSnapshotEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 统计日 */
    private LocalDate statDate;

    /** 统计范围 */
    private String scopeType;

    /** 范围对象 ID */
    private Long scopeId;

    /** 范围名称快照 */
    private String scopeName;

    /** 在职人数 */
    private Integer memberTotal;

    /** 学习人数 */
    private Integer learnerTotal;

    /** 完成人数 */
    private Integer learnerCompleted;

    /** 参考人数 */
    private Integer examTakerTotal;

    /** 通过人数 */
    private Integer examPassedTotal;

    /** 学分总量 */
    private BigDecimal creditTotal;

    /** 获学分人数 */
    private Integer creditEarners;

    /** 计算时间 */
    private LocalDateTime computedAt;
}

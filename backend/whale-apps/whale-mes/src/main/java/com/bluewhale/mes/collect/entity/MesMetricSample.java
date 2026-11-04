package com.bluewhale.mes.collect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采集样本实体，对应表 {@code mes_cl_metric_sample}。
 *
 * <p>幂等键 {@code (tenant_id, point_id, sampled_at, seq)}：网关重推同一样本不会重复入库。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_cl_metric_sample")
public class MesMetricSample extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 采集点 ID */
    private Long pointId;

    /** 采集点编码快照 */
    private String pointCode;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码快照 */
    private String equipmentCode;

    /** 指标值 */
    private BigDecimal metricValue;

    /** 计量单位快照 */
    private String unit;

    /** 采样时间 */
    private LocalDateTime sampledAt;

    /** 同采样点同时间的序号 */
    private Integer seq;

    /** 网关事件幂等键 */
    private String sourceEventId;

    /** 原始报文 */
    private String payload;
}

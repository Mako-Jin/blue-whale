package com.bluewhale.mes.collect.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采集样本视图
 *
 * @author BlueWhale
 */
@Data
public class MesMetricSampleVO {

    /** 样本 ID */
    private Long id;

    /** 采集点 ID */
    private Long pointId;

    /** 采集点编码 */
    private String pointCode;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 指标值 */
    private BigDecimal metricValue;

    /** 计量单位 */
    private String unit;

    /** 采样时间 */
    private LocalDateTime sampledAt;

    /** 序号 */
    private Integer seq;

    /** 原始报文 */
    private String payload;
}

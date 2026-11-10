package com.bluewhale.mes.collect.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集点视图
 *
 * @author BlueWhale
 */
@Data
public class MesCollectPointVO {

    /** 采集点 ID */
    private Long id;

    /** 采集点编码 */
    private String pointCode;

    /** 采集点名称 */
    private String pointName;

    /** 采集设备 ID */
    private Long deviceId;

    /** 采集设备编码 */
    private String deviceCode;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 工序编码 */
    private String operationCode;

    /** 指标类型 */
    private String metricType;

    /** 计量单位 */
    private String unit;

    /** 采集间隔（秒） */
    private Integer collectIntervalSeconds;

    /** 是否启用 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}

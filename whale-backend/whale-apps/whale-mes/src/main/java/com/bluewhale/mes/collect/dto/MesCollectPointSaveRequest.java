package com.bluewhale.mes.collect.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 采集点保存请求
 *
 * @author BlueWhale
 */
@Data
public class MesCollectPointSaveRequest {

    /** 采集点 ID（编辑时必填） */
    private Long id;

    /** 采集点编码（空则编辑时保持不变） */
    @Size(max = 64, message = "{validation.mes.mes-collect-point.point-code-size}")
    private String pointCode;

    /** 采集点名称 */
    @NotBlank(message = "{validation.mes.mes-collect-point.point-name-required}")
    @Size(max = 128, message = "{validation.mes.mes-collect-point.point-name-size}")
    private String pointName;

    /** 采集设备 ID */
    private Long deviceId;

    /** 采集设备编码 */
    @Size(max = 64, message = "{validation.mes.mes-collect-point.device-code-size}")
    private String deviceCode;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码 */
    @Size(max = 64, message = "{validation.mes.mes-collect-point.equipment-code-size}")
    private String equipmentCode;

    /** 工序编码 */
    @Size(max = 64, message = "{validation.mes.mes-collect-point.operation-code-size}")
    private String operationCode;

    /** 指标类型 */
    @Pattern(regexp = "STATUS|COUNT|TEMPERATURE|PRESSURE|SPEED|METRIC", message = "{validation.mes.mes-collect-point.metric-type-pattern}")
    private String metricType;

    /** 计量单位 */
    @Size(max = 16, message = "{validation.mes.mes-collect-point.unit-size}")
    private String unit;

    /** 采集间隔（秒） */
    @Min(value = 1, message = "{validation.mes.mes-collect-point.collect-interval-seconds-min}")
    private Integer collectIntervalSeconds;

    /** 是否启用：0 否 / 1 是 */
    private Integer enabled;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-collect-point.remark-size}")
    private String remark;
}

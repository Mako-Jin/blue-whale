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
    @Size(max = 64, message = "采集点编码长度不能超过 64")
    private String pointCode;

    /** 采集点名称 */
    @NotBlank(message = "采集点名称不能为空")
    @Size(max = 128, message = "采集点名称长度不能超过 128")
    private String pointName;

    /** 采集设备 ID */
    private Long deviceId;

    /** 采集设备编码 */
    @Size(max = 64, message = "采集设备编码长度不能超过 64")
    private String deviceCode;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码 */
    @Size(max = 64, message = "设备编码长度不能超过 64")
    private String equipmentCode;

    /** 工序编码 */
    @Size(max = 64, message = "工序编码长度不能超过 64")
    private String operationCode;

    /** 指标类型 */
    @Pattern(regexp = "STATUS|COUNT|TEMPERATURE|PRESSURE|SPEED|METRIC", message = "指标类型不合法")
    private String metricType;

    /** 计量单位 */
    @Size(max = 16, message = "计量单位长度不能超过 16")
    private String unit;

    /** 采集间隔（秒） */
    @Min(value = 1, message = "采集间隔必须大于 0")
    private Integer collectIntervalSeconds;

    /** 是否启用：0 否 / 1 是 */
    private Integer enabled;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}

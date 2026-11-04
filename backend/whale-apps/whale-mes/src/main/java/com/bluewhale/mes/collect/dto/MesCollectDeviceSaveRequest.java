package com.bluewhale.mes.collect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 采集设备保存请求
 *
 * @author BlueWhale
 */
@Data
public class MesCollectDeviceSaveRequest {

    /** 设备 ID（编辑时必填） */
    private Long id;

    /** 采集设备编码（空则编辑时保持不变） */
    @Size(max = 64, message = "采集设备编码长度不能超过 64")
    private String deviceCode;

    /** 采集设备名称 */
    @NotBlank(message = "采集设备名称不能为空")
    @Size(max = 128, message = "采集设备名称长度不能超过 128")
    private String deviceName;

    /** 类型：GATEWAY / EDGE / SENSOR */
    @Pattern(regexp = "GATEWAY|EDGE|SENSOR", message = "采集设备类型不合法")
    private String deviceType;

    /** 协议：REST / MQTT / OPCUA */
    @Pattern(regexp = "REST|MQTT|OPCUA", message = "协议仅支持 REST / MQTT / OPCUA")
    private String protocol;

    /** 接入地址 */
    @Size(max = 64, message = "接入地址长度不能超过 64")
    private String ipAddress;

    /** 所属产线 ID */
    private Long lineId;

    /** 所属产线编码 */
    @Size(max = 64, message = "产线编码长度不能超过 64")
    private String lineCode;

    /** 状态：ONLINE / OFFLINE / DISABLED */
    @Pattern(regexp = "ONLINE|OFFLINE|DISABLED", message = "状态不合法")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}

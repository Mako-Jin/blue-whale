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
    @Size(max = 64, message = "{validation.mes.mes-collect-device.device-code-size}")
    private String deviceCode;

    /** 采集设备名称 */
    @NotBlank(message = "{validation.mes.mes-collect-device.device-name-required}")
    @Size(max = 128, message = "{validation.mes.mes-collect-device.device-name-size}")
    private String deviceName;

    /** 类型：GATEWAY / EDGE / SENSOR */
    @Pattern(regexp = "GATEWAY|EDGE|SENSOR", message = "{validation.mes.mes-collect-device.device-type-pattern}")
    private String deviceType;

    /** 协议：REST / MQTT / OPCUA */
    @Pattern(regexp = "REST|MQTT|OPCUA", message = "{validation.mes.mes-collect-device.protocol-pattern}")
    private String protocol;

    /** 接入地址 */
    @Size(max = 64, message = "{validation.mes.mes-collect-device.ip-address-size}")
    private String ipAddress;

    /** 所属产线 ID */
    private Long lineId;

    /** 所属产线编码 */
    @Size(max = 64, message = "{validation.mes.mes-collect-device.line-code-size}")
    private String lineCode;

    /** 状态：ONLINE / OFFLINE / DISABLED */
    @Pattern(regexp = "ONLINE|OFFLINE|DISABLED", message = "{validation.mes.mes-collect-device.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-collect-device.remark-size}")
    private String remark;
}

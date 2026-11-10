package com.bluewhale.mes.collect.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集设备视图
 *
 * @author BlueWhale
 */
@Data
public class MesCollectDeviceVO {

    /** 采集设备 ID */
    private Long id;

    /** 采集设备编码 */
    private String deviceCode;

    /** 采集设备名称 */
    private String deviceName;

    /** 类型 */
    private String deviceType;

    /** 协议 */
    private String protocol;

    /** 接入地址 */
    private String ipAddress;

    /** 所属产线 ID */
    private Long lineId;

    /** 所属产线编码 */
    private String lineCode;

    /** 状态 */
    private String status;

    /** 最近心跳时间 */
    private LocalDateTime lastHeartbeatAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}

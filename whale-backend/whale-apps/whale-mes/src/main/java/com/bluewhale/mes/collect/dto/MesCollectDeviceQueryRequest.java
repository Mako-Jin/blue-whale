package com.bluewhale.mes.collect.dto;

import lombok.Data;

/**
 * 采集设备分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesCollectDeviceQueryRequest {

    /** 采集设备编码（模糊） */
    private String deviceCode;

    /** 采集设备名称（模糊） */
    private String deviceName;

    /** 类型 */
    private String deviceType;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}

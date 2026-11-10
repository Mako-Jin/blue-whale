package com.bluewhale.mes.collect.dto;

import lombok.Data;

/**
 * 采集点分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesCollectPointQueryRequest {

    /** 采集点编码（模糊） */
    private String pointCode;

    /** 采集点名称（模糊） */
    private String pointName;

    /** 设备 ID */
    private Long equipmentId;

    /** 采集设备 ID */
    private Long deviceId;

    /** 是否启用 */
    private Integer enabled;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}

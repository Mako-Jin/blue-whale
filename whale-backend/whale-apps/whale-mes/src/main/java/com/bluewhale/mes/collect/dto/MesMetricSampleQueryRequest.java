package com.bluewhale.mes.collect.dto;

import lombok.Data;

/**
 * 采集样本分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesMetricSampleQueryRequest {

    /** 采集点 ID */
    private Long pointId;

    /** 采集点编码（模糊） */
    private String pointCode;

    /** 设备编码（模糊） */
    private String equipmentCode;

    /** 采样开始时间 */
    private String startTime;

    /** 采样结束时间 */
    private String endTime;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}

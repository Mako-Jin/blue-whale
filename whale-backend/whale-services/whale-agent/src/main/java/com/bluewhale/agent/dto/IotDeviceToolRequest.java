package com.bluewhale.agent.dto;

import lombok.Data;

/**
 * IoT 设备工具入参（实时态/历史遥测共用）。
 *
 * <p>设备定位二选一：{@code deviceId}，或 {@code productCode + deviceName}；
 * 历史遥测另有可选过滤字段。</p>
 *
 * @author BlueWhale
 */
@Data
public class IotDeviceToolRequest {

    /** 设备ID */
    private Long deviceId;

    /** 产品编码 */
    private String productCode;

    /** 设备名 */
    private String deviceName;

    /** 属性标识符过滤（历史遥测用，可空） */
    private String identifier;

    /** 起始时间（ISO-8601，历史遥测用，可空） */
    private String from;

    /** 结束时间（ISO-8601，历史遥测用，可空） */
    private String to;

    /** 条数上限（历史遥测用，可空） */
    private Integer limit;
}

package com.bluewhale.oa.attendance.domain.model;

import lombok.Data;

/**
 * 打卡设备。
 *
 * @author BlueWhale
 */
@Data
public class AttendanceDevice {

    private Long id;

    /** 设备编码 */
    private String code;

    /** 设备名称 */
    private String name;

    /** 设备类型 */
    private String deviceType;

    /** 打卡地点ID */
    private Long locationId;

    /** 启停 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}

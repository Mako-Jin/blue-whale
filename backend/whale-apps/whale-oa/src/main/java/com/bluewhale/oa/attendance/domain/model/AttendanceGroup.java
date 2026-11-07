package com.bluewhale.oa.attendance.domain.model;

import lombok.Data;

/**
 * 考勤组。
 *
 * @author BlueWhale
 */
@Data
public class AttendanceGroup {

    private Long id;

    /** 考勤组编码 */
    private String code;

    /** 考勤组名称 */
    private String name;

    /** 班次ID */
    private Long shiftId;

    /** 规则ID */
    private Long ruleId;

    /** 打卡地点ID */
    private Long locationId;

    /** 打卡设备ID */
    private Long deviceId;

    /** 启停 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}

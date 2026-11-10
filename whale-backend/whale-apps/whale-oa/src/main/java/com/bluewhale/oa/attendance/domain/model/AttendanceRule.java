package com.bluewhale.oa.attendance.domain.model;

import lombok.Data;

/**
 * 考勤规则。
 *
 * @author BlueWhale
 */
@Data
public class AttendanceRule {

    private Long id;

    /** 规则编码 */
    private String code;

    /** 规则名称 */
    private String name;

    /** 是否允许外勤 */
    private Integer allowExternal;

    /** 是否要求定位 */
    private Integer requireLocation;

    /** 是否要求设备打卡 */
    private Integer requireDevice;

    /** 每月补卡次数上限 */
    private Integer maxCorrectionsPerMonth;

    /** 补卡可追溯天数 */
    private Integer correctionWindowDays;

    /** 启停 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}

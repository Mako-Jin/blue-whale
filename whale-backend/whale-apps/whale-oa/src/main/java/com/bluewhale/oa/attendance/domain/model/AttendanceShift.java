package com.bluewhale.oa.attendance.domain.model;

import lombok.Data;

import java.time.LocalTime;

/**
 * 考勤班次。
 *
 * @author BlueWhale
 */
@Data
public class AttendanceShift {

    private Long id;

    /** 班次编码 */
    private String code;

    /** 班次名称 */
    private String name;

    /** 上班时间 */
    private LocalTime workStart;

    /** 下班时间 */
    private LocalTime workEnd;

    /** 弹性分钟 */
    private Integer flexMinutes;

    /** 休息时长（分钟） */
    private Integer breakMinutes;

    /** 启停 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}

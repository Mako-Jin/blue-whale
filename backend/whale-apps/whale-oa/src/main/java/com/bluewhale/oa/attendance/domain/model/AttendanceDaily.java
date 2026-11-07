package com.bluewhale.oa.attendance.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考勤日汇总。
 *
 * @author BlueWhale
 */
@Data
public class AttendanceDaily {

    private Long id;

    /** 用户ID */
    private Long userId;

    /** 用户姓名快照 */
    private String userName;

    /** 考勤日期 */
    private LocalDate workDate;

    /** 考勤组ID */
    private Long groupId;

    /** 班次编码快照 */
    private String shiftCode;

    /** 上班时间快照 */
    private LocalTime shiftStart;

    /** 下班时间快照 */
    private LocalTime shiftEnd;

    /** 最早上班打卡 */
    private LocalDateTime firstIn;

    /** 最晚下班打卡 */
    private LocalDateTime lastOut;

    /** 迟到分钟 */
    private Integer lateMinutes;

    /** 早退分钟 */
    private Integer earlyMinutes;

    /** 是否缺卡 */
    private Integer absent;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}

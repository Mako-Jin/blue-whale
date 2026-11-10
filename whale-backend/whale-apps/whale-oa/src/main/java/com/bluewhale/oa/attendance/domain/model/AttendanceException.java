package com.bluewhale.oa.attendance.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤异常。
 *
 * @author BlueWhale
 */
@Data
public class AttendanceException {

    private Long id;

    /** 用户ID */
    private Long userId;

    /** 用户姓名快照 */
    private String userName;

    /** 考勤日期 */
    private LocalDate workDate;

    /** 异常类型：LATE / EARLY / ABSENT */
    private String type;

    /** 异常说明 */
    private String description;

    /** 状态：PENDING / CONFIRMED / WAIVED */
    private String status;

    /** 处理人账号 */
    private String handler;

    /** 处理时间 */
    private LocalDateTime handledTime;

    /** 处理备注 */
    private String remark;
}

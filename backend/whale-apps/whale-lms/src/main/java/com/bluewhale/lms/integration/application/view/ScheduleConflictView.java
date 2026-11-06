package com.bluewhale.lms.integration.application.view;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 培训场次与请假冲突提示。
 *
 * <p>只读提示，不阻断排期也不改请假单：请假是否影响出勤由培训管理员线下判断，
 * LMS 强行拦截反而会把「请假半天但能来上半场」这类正常情况挡在门外。</p>
 *
 * @author BlueWhale
 */
@Data
public class ScheduleConflictView {

    /** 培训班 ID */
    private Long classId;

    /** 场次 ID */
    private Long sessionId;

    /** 场次序号 */
    private Integer sessionNo;

    /** 场次主题 */
    private String sessionTitle;

    /** 场次开始时间 */
    private LocalDateTime startTime;

    /** 场次结束时间 */
    private LocalDateTime endTime;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 请假单号 */
    private String leaveCode;

    /** 假期类型 */
    private String leaveType;

    /** 请假开始日期 */
    private LocalDate leaveStart;

    /** 请假结束日期 */
    private LocalDate leaveEnd;
}

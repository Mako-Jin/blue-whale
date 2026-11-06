package com.bluewhale.lms.training.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 培训班场次（聚合内实体）。
 *
 * <p>课时是讲师课时的最小计量单位：某讲师课时合计等于其名下各场次
 * {@link #hours} 之和，因此场次级校验必须挡住「结束早于开始」「课时为负」
 * 这两类脏数据，否则台账会与场次清单对不上。</p>
 *
 * @author BlueWhale
 */
@Data
public class ClassSession {

    /** 课时上限（与表 DECIMAL(5,1) 对齐） */
    private static final BigDecimal MAX_HOURS = new BigDecimal("999.9");

    /** 主键 */
    private Long id;

    /** 所属培训班 ID */
    private Long classId;

    /** 场次序号（班内唯一，从 1 起） */
    private Integer sessionNo;

    /** 场次主题 */
    private String title;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 场次地点 */
    private String location;

    /** 场次讲师（空则继承班级讲师） */
    private String lecturer;

    /** 本节课时 */
    private BigDecimal hours;

    /**
     * 校验场次时间与课时。
     *
     * <p>时间必须齐全且结束晚于开始；课时不得为空、不得为负、不得超过上限。</p>
     */
    public void validate() {
        if (startTime == null || endTime == null) {
            throw LmsErrorCode.exception(LmsErrorCode.SESSION_TIME_INVALID, "场次开始与结束时间不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw LmsErrorCode.exception(LmsErrorCode.SESSION_TIME_INVALID, "场次结束时间必须晚于开始时间");
        }
        if (hours == null) {
            throw LmsErrorCode.exception(LmsErrorCode.SESSION_TIME_INVALID, "场次课时不能为空");
        }
        if (hours.signum() < 0) {
            throw LmsErrorCode.exception(LmsErrorCode.SESSION_TIME_INVALID, "场次课时不能为负数");
        }
        if (hours.compareTo(MAX_HOURS) > 0) {
            throw LmsErrorCode.exception(LmsErrorCode.SESSION_TIME_INVALID, "场次课时超出上限 999.9");
        }
    }

    /**
     * 判断给定时刻是否落在签到时间窗内（含宽限分钟数）。
     *
     * @param now 当前时刻
     * @param graceMinutes 结束后宽限分钟数（负数按 0 处理）
     * @return 在时间窗内返回 true
     */
    public boolean withinCheckinWindow(LocalDateTime now, int graceMinutes) {
        if (now == null || startTime == null || endTime == null) {
            return false;
        }
        int grace = Math.max(graceMinutes, 0);
        LocalDateTime deadline = endTime.plus(grace, ChronoUnit.MINUTES);
        return !now.isBefore(startTime) && !now.isAfter(deadline);
    }

    /**
     * 解析该场次的有效讲师：场次讲师优先，其次继承班级讲师。
     *
     * @param classLecturer 班级讲师
     * @return 有效讲师，均缺省时返回 null
     */
    public String effectiveLecturer(String classLecturer) {
        if (lecturer != null && !lecturer.isBlank()) {
            return lecturer.trim();
        }
        if (classLecturer != null && !classLecturer.isBlank()) {
            return classLecturer.trim();
        }
        return null;
    }
}

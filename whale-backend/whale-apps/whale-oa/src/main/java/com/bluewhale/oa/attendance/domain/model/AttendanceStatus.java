package com.bluewhale.oa.attendance.domain.model;

/**
 * 考勤状态与类型常量。
 *
 * @author BlueWhale
 */
public final class AttendanceStatus {

    /** 打卡类型：上班 */
    public static final String PUNCH_IN = "IN";

    /** 打卡类型：下班 */
    public static final String PUNCH_OUT = "OUT";

    /** 来源：APP */
    public static final String SOURCE_APP = "APP";

    /** 来源：设备 */
    public static final String SOURCE_DEVICE = "DEVICE";

    /** 来源：网页 */
    public static final String SOURCE_WEB = "WEB";

    /** 日汇总：正常 */
    public static final String DAILY_NORMAL = "NORMAL";

    /** 日汇总：迟到 */
    public static final String DAILY_LATE = "LATE";

    /** 日汇总：早退 */
    public static final String DAILY_EARLY = "EARLY";

    /** 日汇总：迟到且早退 */
    public static final String DAILY_LATE_AND_EARLY = "LATE_AND_EARLY";

    /** 日汇总：缺卡 */
    public static final String DAILY_ABSENT = "ABSENT";

    /** 日汇总：请假 */
    public static final String DAILY_LEAVE = "LEAVE";

    /** 日汇总：出差 */
    public static final String DAILY_OFFICIAL = "OFFICIAL";

    /** 异常：迟到 */
    public static final String EXCEPTION_LATE = "LATE";

    /** 异常：早退 */
    public static final String EXCEPTION_EARLY = "EARLY";

    /** 异常：缺卡 */
    public static final String EXCEPTION_ABSENT = "ABSENT";

    /** 异常状态：待处理 */
    public static final String EXCEPTION_PENDING = "PENDING";

    /** 异常状态：已确认 */
    public static final String EXCEPTION_CONFIRMED = "CONFIRMED";

    /** 异常状态：已豁免 */
    public static final String EXCEPTION_WAIVED = "WAIVED";

    /** 补卡状态：待审批 */
    public static final String CORRECTION_PENDING = "PENDING";

    /** 补卡状态：已通过 */
    public static final String CORRECTION_APPROVED = "APPROVED";

    /** 补卡状态：已驳回 */
    public static final String CORRECTION_REJECTED = "REJECTED";

    /** 补卡状态：已撤销 */
    public static final String CORRECTION_CANCELLED = "CANCELLED";

    /**
     * 工具类私有构造器。
     */
    private AttendanceStatus() {
    }

    /**
     * 校验打卡类型。
     *
     * @param punchType 打卡类型
     * @return 是否合法
     */
    public static boolean isValidPunchType(String punchType) {
        return PUNCH_IN.equals(punchType) || PUNCH_OUT.equals(punchType);
    }

    /**
     * 校验打卡来源。
     *
     * @param source 来源
     * @return 是否合法
     */
    public static boolean isValidSource(String source) {
        return SOURCE_APP.equals(source) || SOURCE_DEVICE.equals(source) || SOURCE_WEB.equals(source);
    }

    /**
     * 校验异常处理状态。
     *
     * @param status 状态
     * @return 是否合法
     */
    public static boolean isValidExceptionStatus(String status) {
        return EXCEPTION_PENDING.equals(status)
                || EXCEPTION_CONFIRMED.equals(status)
                || EXCEPTION_WAIVED.equals(status);
    }
}

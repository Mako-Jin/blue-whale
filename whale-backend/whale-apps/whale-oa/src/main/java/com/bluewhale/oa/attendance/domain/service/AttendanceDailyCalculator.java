package com.bluewhale.oa.attendance.domain.service;

import com.bluewhale.oa.attendance.domain.model.AttendanceShift;
import com.bluewhale.oa.attendance.domain.model.AttendanceStatus;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤日汇总计算器（纯函数，便于单测）。
 *
 * @author BlueWhale
 */
public final class AttendanceDailyCalculator {

    /**
     * 工具类构造器。
     */
    private AttendanceDailyCalculator() {
    }

    /**
     * 计算某员工某日的考勤汇总。
     *
     * <p>判定口径：上班晚于「上班时间 + 弹性」记迟到，下班早于「下班时间 - 弹性」记早退；
     * 缺少上班或下班打卡记缺卡；已通过的请假/出差优先记为请假/出差。</p>
     *
     * @param shift      班次
     * @param firstIn    最早上班打卡，可为空
     * @param lastOut    最晚下班打卡，可为空
     * @param onLeave    当日是否请假
     * @param onOfficial 当日是否出差
     * @param workDate   考勤日期
     * @return 计算结果
     */
    public static DailyComputation compute(AttendanceShift shift,
                                            LocalDateTime firstIn,
                                            LocalDateTime lastOut,
                                            boolean onLeave,
                                            boolean onOfficial,
                                            LocalDate workDate) {
        if (onOfficial) {
            return new DailyComputation(0, 0, 1, AttendanceStatus.DAILY_OFFICIAL, firstIn, lastOut, "出差");
        }
        if (onLeave) {
            return new DailyComputation(0, 0, 1, AttendanceStatus.DAILY_LEAVE, firstIn, lastOut, "请假");
        }
        if (firstIn == null && lastOut == null) {
            return new DailyComputation(0, 0, 1, AttendanceStatus.DAILY_ABSENT, null, null, "无打卡记录");
        }
        int flex = shift.getFlexMinutes() == null ? 0 : shift.getFlexMinutes();
        int lateMinutes = minutesBeyond(firstIn, workDate.atTime(shift.getWorkStart()), flex);
        int earlyMinutes = minutesBeyond(lastOut, workDate.atTime(shift.getWorkEnd()), flex, true);
        if (firstIn == null || lastOut == null) {
            String remark = firstIn == null ? "缺少上班打卡" : "缺少下班打卡";
            return new DailyComputation(lateMinutes, earlyMinutes, 1,
                    AttendanceStatus.DAILY_ABSENT, firstIn, lastOut, remark);
        }
        return new DailyComputation(lateMinutes, earlyMinutes, 0,
                statusOf(lateMinutes, earlyMinutes), firstIn, lastOut, null);
    }

    /**
     * 计算打卡时间超出基准的分钟数。
     *
     * @param punch 打卡时间，为空返回 0
     * @param base 基准时间
     * @param flex 弹性分钟
     * @return 超出分钟数
     */
    private static int minutesBeyond(LocalDateTime punch, LocalDateTime base, int flex) {
        return minutesBeyond(punch, base, flex, false);
    }

    /**
     * 计算打卡时间超出基准的分钟数。
     *
     * @param punch 打卡时间，为空返回 0
     * @param base 基准时间
     * @param flex 弹性分钟
     * @param reverse {@code true} 表示基准晚于打卡（下班早退）
     * @return 超出分钟数
     */
    private static int minutesBeyond(LocalDateTime punch, LocalDateTime base, int flex, boolean reverse) {
        if (punch == null) {
            return 0;
        }
        long minutes = reverse ? Duration.between(punch, base).toMinutes() : Duration.between(base, punch).toMinutes();
        return (int) Math.max(0, minutes - flex);
    }

    /**
     * 根据迟到/早退分钟判定日状态。
     *
     * @param lateMinutes 迟到分钟
     * @param earlyMinutes 早退分钟
     * @return 状态
     */
    private static String statusOf(int lateMinutes, int earlyMinutes) {
        if (lateMinutes > 0 && earlyMinutes > 0) {
            return AttendanceStatus.DAILY_LATE_AND_EARLY;
        }
        if (lateMinutes > 0) {
            return AttendanceStatus.DAILY_LATE;
        }
        if (earlyMinutes > 0) {
            return AttendanceStatus.DAILY_EARLY;
        }
        return AttendanceStatus.DAILY_NORMAL;
    }

    /**
     * 日汇总计算结果。
     */
    @Getter
    public static final class DailyComputation {

        private final int lateMinutes;

        private final int earlyMinutes;

        private final int absent;

        private final String status;

        private final LocalDateTime firstIn;

        private final LocalDateTime lastOut;

        private final String remark;

        /**
         * 构造计算结果。
         *
         * @param lateMinutes 迟到分钟
         * @param earlyMinutes 早退分钟
         * @param absent 是否缺卡
         * @param status 状态
         * @param firstIn 最早上班打卡
         * @param lastOut 最晚下班打卡
         * @param remark 备注
         */
        DailyComputation(int lateMinutes, int earlyMinutes, int absent, String status,
                         LocalDateTime firstIn, LocalDateTime lastOut, String remark) {
            this.lateMinutes = lateMinutes;
            this.earlyMinutes = earlyMinutes;
            this.absent = absent;
            this.status = status;
            this.firstIn = firstIn;
            this.lastOut = lastOut;
            this.remark = remark;
        }
    }
}

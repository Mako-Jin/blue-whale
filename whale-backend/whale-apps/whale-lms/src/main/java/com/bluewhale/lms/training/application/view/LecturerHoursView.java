package com.bluewhale.lms.training.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 讲师课时视图（按讲师聚合）。
 *
 * @author BlueWhale
 */
@Data
public class LecturerHoursView {

    /** 讲师 */
    private String lecturer;

    /** 场次数 */
    private Long sessionCount;

    /** 课时合计（等于其名下各场次课时之和） */
    private BigDecimal totalHours;

    /**
     * 构造视图。
     *
     * @param lecturer 讲师
     * @param sessionCount 场次数
     * @param totalHours 课时合计
     * @return 视图
     */
    public static LecturerHoursView of(String lecturer, long sessionCount, BigDecimal totalHours) {
        LecturerHoursView view = new LecturerHoursView();
        view.setLecturer(lecturer);
        view.setSessionCount(sessionCount);
        view.setTotalHours(totalHours);
        return view;
    }
}

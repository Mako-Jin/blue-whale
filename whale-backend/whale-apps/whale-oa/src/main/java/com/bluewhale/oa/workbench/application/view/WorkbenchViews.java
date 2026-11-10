package com.bluewhale.oa.workbench.application.view;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作台业务视图集合。
 *
 * @author BlueWhale
 */
public final class WorkbenchViews {

    /** 工具类，禁止实例化。 */
    private WorkbenchViews() {
    }

    /**
     * 工作台汇总视图。
     */
    @Data
    public static class SummaryView {

        /** 待办审批数 */
        private long pendingApprovalCount;

        /** 未读公告数 */
        private long unreadNoticeCount;

        /** 今日会议 */
        private List<MeetingItem> todayMeetings;

        /** 到期任务 */
        private List<TaskItem> dueTasks;

        /** 近期生日 */
        private List<BirthdayItem> birthdays;
    }

    /**
     * 今日会议条目。
     */
    @Data
    public static class MeetingItem {

        private Long id;

        private String title;

        private LocalDateTime startTime;

        private LocalDateTime endTime;

        private String roomName;
    }

    /**
     * 到期任务条目。
     */
    @Data
    public static class TaskItem {

        private Long id;

        private String title;

        private LocalDate dueDate;

        private String status;

        private String priority;
    }

    /**
     * 近期生日条目。
     */
    @Data
    public static class BirthdayItem {

        private Long userId;

        private String employeeNo;

        private Long deptId;

        private LocalDate birthday;

        /** 距今天数（0 表示今天） */
        private int daysAhead;
    }
}

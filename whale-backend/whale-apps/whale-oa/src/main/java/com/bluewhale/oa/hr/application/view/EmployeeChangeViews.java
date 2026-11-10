package com.bluewhale.oa.hr.application.view;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人员异动视图。
 *
 * @author BlueWhale
 */
public final class EmployeeChangeViews {

    /** 工具类，禁止实例化。 */
    private EmployeeChangeViews() {
    }

    /**
     * 列表项。
     */
    @Data
    public static class ListItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String changeNo;
        private String changeType;
        private String changeTypeLabel;
        private Long userId;
        private String employeeName;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate effectiveDate;
        private String status;
        private String applicantName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime executedTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }

    /**
     * 详情（含离职清单任务）。
     */
    @Data
    public static class Detail implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String changeNo;
        private String changeType;
        private String changeTypeLabel;
        private Long userId;
        private String employeeName;
        private String username;
        private Long fromDeptId;
        private Long toDeptId;
        private String fromStatus;
        private String toStatus;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate effectiveDate;
        private String reason;
        private String status;
        private Long approvalInstanceId;
        private Long applicantId;
        private String applicantName;
        private Long executorId;
        private String executorName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime executedTime;
        private String executeResult;
        private String remark;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;
        private List<Task> tasks;
    }

    /**
     * 离职清单任务。
     */
    @Data
    public static class Task implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String taskCode;
        private String taskName;
        private Long assigneeId;
        private String assigneeName;
        private String status;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime doneTime;
        private String remark;
    }

    /**
     * 执行结果。
     */
    @Data
    public static class ExecuteResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String status;
        private String message;
        private String temporaryPassword;
    }
}

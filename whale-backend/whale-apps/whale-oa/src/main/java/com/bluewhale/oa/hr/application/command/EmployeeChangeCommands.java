package com.bluewhale.oa.hr.application.command;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 人员异动命令。
 *
 * @author BlueWhale
 */
public final class EmployeeChangeCommands {

    /** 工具类，禁止实例化。 */
    private EmployeeChangeCommands() {
    }

    /**
     * 保存异动单（新建或更新草稿）。
     */
    @Data
    public static class Save implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 异动类型 */
        @NotBlank(message = "{validation.oa.save.change-type-required}")
        private String changeType;

        /** 目标用户ID（入职未建号时为空） */
        private Long userId;

        /** 员工姓名 */
        @NotBlank(message = "{validation.oa.save.employee-name-required}")
        @Size(max = 64, message = "{validation.oa.save.employee-name-size}")
        private String employeeName;

        /** 登录账号（入职建号时填写） */
        @Size(max = 64, message = "{validation.oa.save.username-size}")
        private String username;

        /** 原部门ID */
        private Long fromDeptId;

        /** 目标部门ID */
        private Long toDeptId;

        /** 原在职状态 */
        @Size(max = 16, message = "{validation.oa.save.from-status-size}")
        private String fromStatus;

        /** 目标在职状态 */
        @Size(max = 16, message = "{validation.oa.save.to-status-size}")
        private String toStatus;

        /** 生效日期 */
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate effectiveDate;

        /** 异动原因 */
        @Size(max = 512, message = "{validation.oa.save.reason-size}")
        private String reason;

        /** 备注 */
        @Size(max = 255, message = "{validation.oa.save.remark-size}")
        private String remark;
    }

    /**
     * 任务完成命令。
     */
    @Data
    public static class TaskDone implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 备注 */
        @Size(max = 255, message = "{validation.oa.task-done.remark-size}")
        private String remark;
    }
}

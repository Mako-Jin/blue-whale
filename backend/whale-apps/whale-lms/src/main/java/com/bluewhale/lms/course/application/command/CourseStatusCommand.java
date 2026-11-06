package com.bluewhale.lms.course.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 课程状态变更命令。
 *
 * @author BlueWhale
 */
@Data
public class CourseStatusCommand {

    /** 目标动作：PUBLISH / ARCHIVE */
    @NotBlank(message = "状态动作不能为空")
    private String action;
}

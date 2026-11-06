package com.bluewhale.lms.course.application.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 学习进度上报命令。
 *
 * @author BlueWhale
 */
@Data
public class ProgressReportCommand {

    /** 章节 ID */
    @NotNull(message = "章节 ID 不能为空")
    private Long chapterId;

    /** 完成百分比（0–100） */
    @NotNull(message = "完成百分比不能为空")
    @Min(value = 0, message = "完成百分比不能小于 0")
    @Max(value = 100, message = "完成百分比不能大于 100")
    private Integer percent;
}

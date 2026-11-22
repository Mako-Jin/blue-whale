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
    @NotNull(message = "{validation.lms.progress-report.chapter-id-required}")
    private Long chapterId;

    /** 完成百分比（0–100） */
    @NotNull(message = "{validation.lms.progress-report.percent-required}")
    @Min(value = 0, message = "{validation.lms.progress-report.percent-min}")
    @Max(value = 100, message = "{validation.lms.progress-report.percent-max}")
    private Integer percent;
}

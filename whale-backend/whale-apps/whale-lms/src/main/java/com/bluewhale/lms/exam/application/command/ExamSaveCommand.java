package com.bluewhale.lms.exam.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试保存命令（新增 / 编辑）。
 *
 * <p>考试只引用已发布试卷，不复制题目：换卷就改 {@code paperId}，历史作答
 * 已把 {@code paperId} 快照在作答记录上，不受影响。</p>
 *
 * @author BlueWhale
 */
@Data
public class ExamSaveCommand {

    /** 考试编码（新增必填） */
    @Size(max = 64, message = "{validation.lms.exam.exam-code-size}")
    private String examCode;

    /** 考试名称 */
    @NotBlank(message = "{validation.lms.exam.title-required}")
    @Size(max = 128, message = "{validation.lms.exam.title-size}")
    private String title;

    /** 试卷 ID */
    @NotNull(message = "{validation.lms.exam.paper-id-required}")
    private Long paperId;

    /** 关联课程 ID */
    private Long courseId;

    /** 开考时间 */
    @NotNull(message = "{validation.lms.exam.start-time-required}")
    private LocalDateTime startTime;

    /** 截止时间 */
    @NotNull(message = "{validation.lms.exam.end-time-required}")
    private LocalDateTime endTime;

    /** 单次作答时长（分钟） */
    @NotNull(message = "{validation.lms.exam.duration-minutes-required}")
    @Min(value = 1, message = "{validation.lms.exam.duration-minutes-min}")
    @Max(value = 600, message = "{validation.lms.exam.duration-minutes-max}")
    private Integer durationMinutes;

    /** 最大作答次数 */
    @NotNull(message = "{validation.lms.exam.max-attempts-required}")
    @Min(value = 1, message = "{validation.lms.exam.max-attempts-min}")
    @Max(value = 20, message = "{validation.lms.exam.max-attempts-max}")
    private Integer maxAttempts;

    /** 是否乱序（只打乱呈现次序，不影响判分） */
    private Boolean shuffle;

    /** 及格分 */
    @DecimalMin(value = "0.0", message = "{validation.lms.exam.pass-score-min}")
    private BigDecimal passScore;

    /** 切屏次数上限（0 不限） */
    @Min(value = 0, message = "{validation.lms.exam.switch-limit-min}")
    private Integer switchLimit;

    /** 备注 */
    @Size(max = 500, message = "{validation.lms.exam.remark-size}")
    private String remark;
}

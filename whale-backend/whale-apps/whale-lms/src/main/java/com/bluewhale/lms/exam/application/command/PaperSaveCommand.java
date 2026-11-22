package com.bluewhale.lms.exam.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 试卷保存命令（新增 / 编辑）。
 *
 * <p>总分与题目数由明细重算，不接受前端提交。</p>
 *
 * @author BlueWhale
 */
@Data
public class PaperSaveCommand {

    /** 试卷编码（新增必填） */
    @Size(max = 64, message = "{validation.lms.paper.paper-code-size}")
    private String paperCode;

    /** 试卷名称 */
    @NotBlank(message = "{validation.lms.paper.title-required}")
    @Size(max = 128, message = "{validation.lms.paper.title-size}")
    private String title;

    /** 关联课程 ID */
    private Long courseId;

    /** 考试时长（分钟） */
    @NotNull(message = "{validation.lms.paper.duration-minutes-required}")
    @Min(value = 1, message = "{validation.lms.paper.duration-minutes-min}")
    @Max(value = 600, message = "{validation.lms.paper.duration-minutes-max}")
    private Integer durationMinutes;

    /** 及格分 */
    @DecimalMin(value = "0.0", message = "{validation.lms.paper.pass-score-min}")
    private BigDecimal passScore;

    /** 备注 */
    @Size(max = 500, message = "{validation.lms.paper.remark-size}")
    private String remark;
}

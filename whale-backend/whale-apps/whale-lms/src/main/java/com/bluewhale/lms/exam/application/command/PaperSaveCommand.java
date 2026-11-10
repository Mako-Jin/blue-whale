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
    @Size(max = 64, message = "试卷编码最长 64 字符")
    private String paperCode;

    /** 试卷名称 */
    @NotBlank(message = "试卷名称不能为空")
    @Size(max = 128, message = "试卷名称最长 128 字符")
    private String title;

    /** 关联课程 ID */
    private Long courseId;

    /** 考试时长（分钟） */
    @NotNull(message = "考试时长不能为空")
    @Min(value = 1, message = "考试时长至少 1 分钟")
    @Max(value = 600, message = "考试时长最多 600 分钟")
    private Integer durationMinutes;

    /** 及格分 */
    @DecimalMin(value = "0.0", message = "及格分不能为负")
    private BigDecimal passScore;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字符")
    private String remark;
}

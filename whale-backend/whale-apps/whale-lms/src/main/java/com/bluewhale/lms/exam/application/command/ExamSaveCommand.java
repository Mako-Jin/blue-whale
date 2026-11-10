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
    @Size(max = 64, message = "考试编码最长 64 字符")
    private String examCode;

    /** 考试名称 */
    @NotBlank(message = "考试名称不能为空")
    @Size(max = 128, message = "考试名称最长 128 字符")
    private String title;

    /** 试卷 ID */
    @NotNull(message = "试卷不能为空")
    private Long paperId;

    /** 关联课程 ID */
    private Long courseId;

    /** 开考时间 */
    @NotNull(message = "开考时间不能为空")
    private LocalDateTime startTime;

    /** 截止时间 */
    @NotNull(message = "截止时间不能为空")
    private LocalDateTime endTime;

    /** 单次作答时长（分钟） */
    @NotNull(message = "单次作答时长不能为空")
    @Min(value = 1, message = "单次作答时长至少 1 分钟")
    @Max(value = 600, message = "单次作答时长最多 600 分钟")
    private Integer durationMinutes;

    /** 最大作答次数 */
    @NotNull(message = "最大作答次数不能为空")
    @Min(value = 1, message = "最大作答次数至少 1 次")
    @Max(value = 20, message = "最大作答次数最多 20 次")
    private Integer maxAttempts;

    /** 是否乱序（只打乱呈现次序，不影响判分） */
    private Boolean shuffle;

    /** 及格分 */
    @DecimalMin(value = "0.0", message = "及格分不能为负")
    private BigDecimal passScore;

    /** 切屏次数上限（0 不限） */
    @Min(value = 0, message = "切屏次数上限不能为负")
    private Integer switchLimit;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字符")
    private String remark;
}

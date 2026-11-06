package com.bluewhale.lms.training.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 培训班保存命令。
 *
 * @author BlueWhale
 */
@Data
public class TrainingClassSaveCommand {

    /** 培训班编码（租户内唯一，服务端大写归一） */
    @NotBlank(message = "培训班编码不能为空")
    @Size(max = 64, message = "培训班编码不得超过 64 字")
    private String classCode;

    /** 培训班名称 */
    @NotBlank(message = "培训班名称不能为空")
    @Size(max = 128, message = "培训班名称不得超过 128 字")
    private String title;

    /** 关联课程 ID（线上培训可空） */
    private Long courseId;

    /** 形式：ONLINE / OFFLINE / BLENDED */
    private String mode;

    /** 主讲讲师 */
    @Size(max = 64, message = "讲师不得超过 64 字")
    private String lecturer;

    /** 开班日期 */
    @NotNull(message = "开班日期不能为空")
    private LocalDate startDate;

    /** 结班日期 */
    @NotNull(message = "结班日期不能为空")
    private LocalDate endDate;

    /** 培训地点 */
    @Size(max = 256, message = "培训地点不得超过 256 字")
    private String location;

    /** 计划人数（空为不限） */
    private Integer capacity;

    /** 备注 */
    @Size(max = 500, message = "备注不得超过 500 字")
    private String remark;
}

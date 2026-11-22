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
    @NotBlank(message = "{validation.lms.training-class.class-code-required}")
    @Size(max = 64, message = "{validation.lms.training-class.class-code-size}")
    private String classCode;

    /** 培训班名称 */
    @NotBlank(message = "{validation.lms.training-class.title-required}")
    @Size(max = 128, message = "{validation.lms.training-class.title-size}")
    private String title;

    /** 关联课程 ID（线上培训可空） */
    private Long courseId;

    /** 形式：ONLINE / OFFLINE / BLENDED */
    private String mode;

    /** 主讲讲师 */
    @Size(max = 64, message = "{validation.lms.training-class.lecturer-size}")
    private String lecturer;

    /** 开班日期 */
    @NotNull(message = "{validation.lms.training-class.start-date-required}")
    private LocalDate startDate;

    /** 结班日期 */
    @NotNull(message = "{validation.lms.training-class.end-date-required}")
    private LocalDate endDate;

    /** 培训地点 */
    @Size(max = 256, message = "{validation.lms.training-class.location-size}")
    private String location;

    /** 计划人数（空为不限） */
    private Integer capacity;

    /** 备注 */
    @Size(max = 500, message = "{validation.lms.training-class.remark-size}")
    private String remark;
}

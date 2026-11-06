package com.bluewhale.lms.training.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 培训班场次保存命令。
 *
 * @author BlueWhale
 */
@Data
public class SessionSaveCommand {

    /** 场次序号（班内唯一，从 1 起；空则由服务端按现有场次数 +1） */
    private Integer sessionNo;

    /** 场次主题 */
    @NotBlank(message = "场次主题不能为空")
    @Size(max = 128, message = "场次主题不得超过 128 字")
    private String title;

    /** 开始时间 */
    @NotNull(message = "场次开始时间不能为空")
    private LocalDateTime startTime;

    /** 结束时间 */
    @NotNull(message = "场次结束时间不能为空")
    private LocalDateTime endTime;

    /** 场次地点 */
    @Size(max = 256, message = "场次地点不得超过 256 字")
    private String location;

    /** 场次讲师（空则继承班级讲师） */
    @Size(max = 64, message = "讲师不得超过 64 字")
    private String lecturer;

    /** 本节课时 */
    @NotNull(message = "场次课时不能为空")
    @DecimalMin(value = "0.0", message = "场次课时不能为负数")
    private BigDecimal hours;
}

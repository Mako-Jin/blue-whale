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
    @NotBlank(message = "{validation.lms.session.title-required}")
    @Size(max = 128, message = "{validation.lms.session.title-size}")
    private String title;

    /** 开始时间 */
    @NotNull(message = "{validation.lms.session.start-time-required}")
    private LocalDateTime startTime;

    /** 结束时间 */
    @NotNull(message = "{validation.lms.session.end-time-required}")
    private LocalDateTime endTime;

    /** 场次地点 */
    @Size(max = 256, message = "{validation.lms.session.location-size}")
    private String location;

    /** 场次讲师（空则继承班级讲师） */
    @Size(max = 64, message = "{validation.lms.session.lecturer-size}")
    private String lecturer;

    /** 本节课时 */
    @NotNull(message = "{validation.lms.session.hours-required}")
    @DecimalMin(value = "0.0", message = "{validation.lms.session.hours-min}")
    private BigDecimal hours;
}

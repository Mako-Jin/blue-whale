package com.bluewhale.crm.service.application.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工作日历保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ServiceCalendarCommand {

    /** 日历编码 */
    private String calendarCode;

    /** 上班时间 HH:mm */
    @NotBlank
    private String workdayStart;

    /** 下班时间 HH:mm */
    @NotBlank
    private String workdayEnd;

    /** 午休分钟数 */
    @NotNull
    @Min(0)
    private Integer breakMinutes;

    /** 工作日（ISO 1–7，逗号分隔） */
    @NotBlank
    private String weekdays;

    /** 是否启用 */
    private Boolean enabled;
}

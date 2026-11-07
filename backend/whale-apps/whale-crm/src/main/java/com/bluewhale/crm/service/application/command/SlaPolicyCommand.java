package com.bluewhale.crm.service.application.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单档 SLA 策略命令。
 *
 * @author BlueWhale
 */
@Data
public class SlaPolicyCommand {

    /** 优先级 P1/P2/P3 */
    @NotBlank
    private String priority;

    /** 响应时限（工作小时） */
    @NotNull
    @Min(1)
    private Integer responseHours;

    /** 解决时限（工作小时） */
    @NotNull
    @Min(1)
    private Integer resolveHours;

    /** 是否启用 */
    private Boolean enabled;
}

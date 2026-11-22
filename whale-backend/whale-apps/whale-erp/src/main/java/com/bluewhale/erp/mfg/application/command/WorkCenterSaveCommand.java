package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工作中心新增/编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class WorkCenterSaveCommand {

    /** 编码 */
    @NotBlank(message = "{validation.erp.work-center.code-required}")
    @Size(max = 64, message = "{validation.erp.work-center.code-size}")
    private String code;

    /** 名称 */
    @NotBlank(message = "{validation.erp.work-center.name-required}")
    @Size(max = 128, message = "{validation.erp.work-center.name-size}")
    private String name;

    /** 所属部门 */
    @Size(max = 128, message = "{validation.erp.work-center.department-size}")
    private String department;

    /** 日产能工时 */
    @NotNull(message = "{validation.erp.work-center.daily-capacity-hours-required}")
    @DecimalMin(value = "0.01", message = "{validation.erp.work-center.daily-capacity-hours-min}")
    private BigDecimal dailyCapacityHours;

    /** 效率百分比 */
    @DecimalMin(value = "0", message = "{validation.erp.work-center.efficiency-rate-min}")
    @DecimalMax(value = "100", message = "{validation.erp.work-center.efficiency-rate-max}")
    private BigDecimal efficiencyRate;

    /** 工作日（ISO 1–7，逗号分隔） */
    @Size(max = 20, message = "{validation.erp.work-center.work-days-size}")
    private String workDays;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.erp.work-center.remark-size}")
    private String remark;
}

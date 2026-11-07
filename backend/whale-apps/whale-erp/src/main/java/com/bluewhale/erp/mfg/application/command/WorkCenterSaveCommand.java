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
    @NotBlank(message = "工作中心编码不能为空")
    @Size(max = 64, message = "工作中心编码长度不能超过 64")
    private String code;

    /** 名称 */
    @NotBlank(message = "工作中心名称不能为空")
    @Size(max = 128, message = "工作中心名称长度不能超过 128")
    private String name;

    /** 所属部门 */
    @Size(max = 128, message = "所属部门长度不能超过 128")
    private String department;

    /** 日产能工时 */
    @NotNull(message = "日产能工时不能为空")
    @DecimalMin(value = "0.01", message = "日产能工时必须大于 0")
    private BigDecimal dailyCapacityHours;

    /** 效率百分比 */
    @DecimalMin(value = "0", message = "效率不能小于 0")
    @DecimalMax(value = "100", message = "效率不能大于 100")
    private BigDecimal efficiencyRate;

    /** 工作日（ISO 1–7，逗号分隔） */
    @Size(max = 20, message = "工作日长度不能超过 20")
    private String workDays;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}

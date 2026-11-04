package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保养计划保存请求
 *
 * @author BlueWhale
 */
@Data
public class MesMaintenancePlanSaveRequest {

    /** 计划 ID（编辑时必填） */
    private Long id;

    /** 计划编号（空则自动生成） */
    @Size(max = 64, message = "计划编号长度不能超过 64")
    private String planNo;

    /** 设备 ID */
    @NotNull(message = "设备不能为空")
    private Long equipmentId;

    /** 保养类型 */
    @Pattern(regexp = "ROUTINE|INSPECTION|OVERHAUL", message = "保养类型不合法")
    private String maintenanceType;

    /** 保养周期（天） */
    @Min(value = 1, message = "保养周期必须大于 0")
    private Integer cycleDays;

    /** 保养项清单（分号分隔） */
    @NotBlank(message = "保养项不能为空")
    @Size(max = 1000, message = "保养项清单长度不能超过 1000")
    private String checklist;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED / DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}

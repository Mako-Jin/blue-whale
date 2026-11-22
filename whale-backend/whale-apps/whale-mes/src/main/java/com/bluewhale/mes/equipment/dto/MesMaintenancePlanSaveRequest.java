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
    @Size(max = 64, message = "{validation.mes.mes-maintenance-plan.plan-no-size}")
    private String planNo;

    /** 设备 ID */
    @NotNull(message = "{validation.mes.mes-maintenance-plan.equipment-id-required}")
    private Long equipmentId;

    /** 保养类型 */
    @Pattern(regexp = "ROUTINE|INSPECTION|OVERHAUL", message = "{validation.mes.mes-maintenance-plan.maintenance-type-pattern}")
    private String maintenanceType;

    /** 保养周期（天） */
    @Min(value = 1, message = "{validation.mes.mes-maintenance-plan.cycle-days-min}")
    private Integer cycleDays;

    /** 保养项清单（分号分隔） */
    @NotBlank(message = "{validation.mes.mes-maintenance-plan.checklist-required}")
    @Size(max = 1000, message = "{validation.mes.mes-maintenance-plan.checklist-size}")
    private String checklist;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.mes.mes-maintenance-plan.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-maintenance-plan.remark-size}")
    private String remark;
}

package com.bluewhale.erp.qc.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新增保养工单命令。
 *
 * @author BlueWhale
 */
@Data
public class MaintenanceOrderCreateCommand {

    /** 设备编码 */
    @NotBlank
    private String equipmentNo;

    /** 保养计划主键 */
    private Long planId;

    /** 类型：PREVENTIVE / CORRECTIVE */
    @NotBlank
    private String maintenanceType;

    /** 计划保养日期 */
    @NotNull
    private LocalDate planDate;

    /** 备注 */
    private String remark;
}

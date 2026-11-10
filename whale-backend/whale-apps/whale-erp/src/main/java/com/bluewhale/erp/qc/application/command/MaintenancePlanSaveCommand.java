package com.bluewhale.erp.qc.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 保养计划新增/编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class MaintenancePlanSaveCommand {

    /** 设备编码 */
    @NotBlank
    private String equipmentNo;

    /** 保养周期（天） */
    @NotNull
    private Integer cycleDays;

    /** 上次保养日期 */
    private LocalDate lastDate;

    /** 下次保养日期（为空时按上次保养日 + 周期推算） */
    private LocalDate nextDate;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    private String remark;
}

package com.bluewhale.erp.qc.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 校准计划新增/编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class CalibrationPlanSaveCommand {

    /** 量具编号 */
    @NotBlank
    private String toolNo;

    /** 校准周期（月） */
    @NotNull
    private Integer cycleMonths;

    /** 下次校准日期 */
    @NotNull
    private LocalDate nextDate;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    private String remark;
}

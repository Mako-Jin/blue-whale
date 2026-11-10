package com.bluewhale.erp.qc.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 量具新增/编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class MeasuringToolSaveCommand {

    /** 量具编号 */
    @NotBlank
    private String toolNo;

    /** 量具名称 */
    @NotBlank
    private String name;

    /** 量具类型 */
    private String toolType;

    /** 精度值描述 */
    private String precisionValue;

    /** 校准周期（月） */
    @NotNull
    private Integer calibrationCycleMonths;

    /** 上次校准日期 */
    private LocalDate lastCalibrationDate;

    /** 下次校准日期（为空时按上次校准日 + 周期推算） */
    private LocalDate nextCalibrationDate;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    private String remark;
}

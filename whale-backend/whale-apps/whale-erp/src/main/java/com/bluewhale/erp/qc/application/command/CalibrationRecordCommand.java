package com.bluewhale.erp.qc.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 量具校准记录命令。
 *
 * @author BlueWhale
 */
@Data
public class CalibrationRecordCommand {

    /** 量具编号 */
    @NotBlank
    private String toolNo;

    /** 校准计划主键 */
    private Long planId;

    /** 校准日期 */
    @NotNull
    private LocalDate calibrationDate;

    /** 校准结论：PASS / FAIL */
    @NotBlank
    private String result;

    /** 校准证书号 */
    private String certificateNo;

    /** 备注 */
    private String remark;
}

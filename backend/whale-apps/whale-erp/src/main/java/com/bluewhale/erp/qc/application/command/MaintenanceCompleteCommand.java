package com.bluewhale.erp.qc.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保养工单完成命令。
 *
 * @author BlueWhale
 */
@Data
public class MaintenanceCompleteCommand {

    /** 实际完成日期 */
    @NotNull
    private LocalDate completeDate;

    /** 保养费用 */
    private BigDecimal costAmount;

    /** 保养结果 */
    private String result;

    /** 备注 */
    private String remark;
}

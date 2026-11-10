package com.bluewhale.party.fee.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 党费缴纳登记命令。
 *
 * @author BlueWhale
 */
@Data
public class FeePayCommand {

    /** 本次缴纳金额 */
    @NotNull(message = "缴纳金额不能为空")
    @DecimalMin(value = "0.01", message = "缴纳金额必须大于 0")
    private BigDecimal amount;

    /** 缴费日期（空表示当天） */
    private LocalDate paidDate;

    /** 备注（补缴说明等） */
    private String remark;
}

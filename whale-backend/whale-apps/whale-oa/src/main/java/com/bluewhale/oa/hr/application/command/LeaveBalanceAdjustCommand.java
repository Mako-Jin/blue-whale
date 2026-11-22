package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 假期额度调整命令。
 *
 * @author BlueWhale
 */
@Data
public class LeaveBalanceAdjustCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新的总额度 */
    @NotNull(message = "{validation.oa.leave-balance-adjust.total-days-required}")
    @DecimalMin(value = "0.0", message = "{validation.oa.leave-balance-adjust.total-days-min}")
    private BigDecimal totalDays;

    /** 备注 */
    @Size(max = 255, message = "{validation.oa.leave-balance-adjust.remark-size}")
    private String remark;
}

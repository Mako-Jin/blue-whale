package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 年度假期额度初始化命令。
 *
 * @author BlueWhale
 */
@Data
public class LeaveBalanceInitCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    @NotNull(message = "{validation.oa.leave-balance-init.user-id-required}")
    private Long userId;

    /** 额度年份 */
    @NotNull(message = "{validation.oa.leave-balance-init.year-required}")
    @Min(value = 2000, message = "{validation.oa.leave-balance-init.year-min}")
    @Max(value = 2100, message = "{validation.oa.leave-balance-init.year-max}")
    private Integer year;
}

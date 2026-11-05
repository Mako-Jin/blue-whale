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
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 额度年份 */
    @NotNull(message = "额度年份不能为空")
    @Min(value = 2000, message = "额度年份不能早于 2000")
    @Max(value = 2100, message = "额度年份不能晚于 2100")
    private Integer year;
}

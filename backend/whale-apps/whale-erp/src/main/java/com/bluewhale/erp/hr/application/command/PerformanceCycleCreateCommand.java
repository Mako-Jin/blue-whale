package com.bluewhale.erp.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新建绩效周期命令。
 *
 * @author BlueWhale
 */
@Data
public class PerformanceCycleCreateCommand {

    /** 周期名称 */
    @NotBlank
    private String name;

    /** 考核期间（YYYY-MM） */
    @NotBlank
    private String period;

    /** 考核开始日期 */
    @NotNull
    private LocalDate startDate;

    /** 考核结束日期 */
    @NotNull
    private LocalDate endDate;

    /** 备注 */
    private String remark;
}

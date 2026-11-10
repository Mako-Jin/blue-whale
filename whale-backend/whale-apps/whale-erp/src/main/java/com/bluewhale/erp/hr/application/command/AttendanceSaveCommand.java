package com.bluewhale.erp.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 考勤汇总保存命令（按员工 + 期间 upsert）。
 *
 * @author BlueWhale
 */
@Data
public class AttendanceSaveCommand {

    /** 员工编号 */
    @NotBlank
    private String employeeNo;

    /** 考勤期间（YYYY-MM） */
    @NotBlank
    private String period;

    /** 应出勤天数 */
    private BigDecimal workDays;

    /** 实际出勤天数 */
    private BigDecimal actualDays;

    /** 请假小时数 */
    private BigDecimal leaveHours;

    /** 加班小时数 */
    private BigDecimal overtimeHours;

    /** 缺勤天数 */
    private BigDecimal absentDays;

    /** 备注 */
    private String remark;
}

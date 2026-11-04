package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 考勤汇总。
 *
 * <p>不变量：员工编号与期间非空；出勤、请假、加班与缺勤均非负。</p>
 *
 * @author BlueWhale
 */
@Data
public class AttendanceSummary {

    /** 主键 */
    private Long id;

    /** 员工编号 */
    private String employeeNo;

    /** 考勤期间（YYYY-MM） */
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

    /**
     * 校验考勤数据。
     */
    public void validate() {
        if (employeeNo == null || employeeNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "考勤员工不能为空");
        }
        if (period == null || period.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "考勤期间不能为空");
        }
        requireNonNegative(workDays, "应出勤天数");
        requireNonNegative(actualDays, "实际出勤天数");
        requireNonNegative(leaveHours, "请假小时数");
        requireNonNegative(overtimeHours, "加班小时数");
        requireNonNegative(absentDays, "缺勤天数");
    }

    /**
     * 校验数值非负。
     *
     * @param value 数值
     * @param label 字段名
     */
    private void requireNonNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, label + "不能为负数");
        }
    }
}

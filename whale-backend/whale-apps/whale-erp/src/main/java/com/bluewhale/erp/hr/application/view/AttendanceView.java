package com.bluewhale.erp.hr.application.view;

import com.bluewhale.erp.hr.domain.model.AttendanceSummary;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 考勤汇总视图。
 *
 * @author BlueWhale
 */
@Data
public class AttendanceView {

    /** 主键 */
    private Long id;

    /** 员工编号 */
    private String employeeNo;

    /** 考勤期间 */
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
     * 由领域对象构建视图。
     *
     * @param attendance 考勤
     * @return 视图
     */
    public static AttendanceView of(AttendanceSummary attendance) {
        AttendanceView view = new AttendanceView();
        view.setId(attendance.getId());
        view.setEmployeeNo(attendance.getEmployeeNo());
        view.setPeriod(attendance.getPeriod());
        view.setWorkDays(attendance.getWorkDays());
        view.setActualDays(attendance.getActualDays());
        view.setLeaveHours(attendance.getLeaveHours());
        view.setOvertimeHours(attendance.getOvertimeHours());
        view.setAbsentDays(attendance.getAbsentDays());
        view.setRemark(attendance.getRemark());
        return view;
    }
}

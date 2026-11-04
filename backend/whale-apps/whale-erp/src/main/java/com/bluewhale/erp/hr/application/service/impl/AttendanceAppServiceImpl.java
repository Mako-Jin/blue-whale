package com.bluewhale.erp.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.hr.application.command.AttendanceSaveCommand;
import com.bluewhale.erp.hr.application.service.AttendanceAppService;
import com.bluewhale.erp.hr.application.view.AttendanceView;
import com.bluewhale.erp.hr.domain.model.AttendanceSummary;
import com.bluewhale.erp.hr.domain.model.Employee;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.repository.AttendanceRepository;
import com.bluewhale.erp.hr.domain.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 考勤汇总应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AttendanceAppServiceImpl implements AttendanceAppService {

    /** 期间格式 */
    private static final String PERIOD_PATTERN = "\\d{4}-\\d{2}";

    private final AttendanceRepository attendanceRepository;

    private final EmployeeRepository employeeRepository;

    @Override
    public PageResult<AttendanceView> page(HrQuery query) {
        List<AttendanceView> records = attendanceRepository.page(query).stream()
                .map(AttendanceView::of).toList();
        return PageResult.of(records, attendanceRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceView save(AttendanceSaveCommand command) {
        String period = requirePeriod(command.getPeriod());
        Employee employee = employeeRepository.findByEmployeeNo(command.getEmployeeNo())
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.EMPLOYEE_NOT_FOUND));
        AttendanceSummary attendance = attendanceRepository.find(employee.getEmployeeNo(), period)
                .orElseGet(() -> {
                    AttendanceSummary created = new AttendanceSummary();
                    created.setEmployeeNo(employee.getEmployeeNo());
                    created.setPeriod(period);
                    return created;
                });
        boolean exists = attendance.getId() != null;
        attendance.setWorkDays(decimal(command.getWorkDays()));
        attendance.setActualDays(decimal(command.getActualDays()));
        attendance.setLeaveHours(decimal(command.getLeaveHours()));
        attendance.setOvertimeHours(decimal(command.getOvertimeHours()));
        attendance.setAbsentDays(decimal(command.getAbsentDays()));
        attendance.setRemark(command.getRemark());
        attendance.validate();
        if (exists) {
            attendanceRepository.update(attendance);
        } else {
            attendanceRepository.insert(attendance);
        }
        return AttendanceView.of(attendance);
    }

    /**
     * 校验期间格式并去除空白。
     *
     * @param period 期间
     * @return 规范化期间
     */
    private String requirePeriod(String period) {
        String value = period == null ? "" : period.trim();
        if (!value.matches(PERIOD_PATTERN)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "考勤期间格式应为 YYYY-MM");
        }
        return value;
    }

    /**
     * 空值转零。
     *
     * @param value 数值
     * @return 非空数值
     */
    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

package com.bluewhale.erp.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.HrPayrollProperties;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.hr.application.command.PayrollCreateCommand;
import com.bluewhale.erp.hr.application.service.PayrollAppService;
import com.bluewhale.erp.hr.application.view.PayrollRunView;
import com.bluewhale.erp.hr.domain.model.AttendanceSummary;
import com.bluewhale.erp.hr.domain.model.Employee;
import com.bluewhale.erp.hr.domain.model.HrPayrollRates;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.HrStatus;
import com.bluewhale.erp.hr.domain.model.PayrollRun;
import com.bluewhale.erp.hr.domain.model.Payslip;
import com.bluewhale.erp.hr.domain.repository.AttendanceRepository;
import com.bluewhale.erp.hr.domain.repository.EmployeeRepository;
import com.bluewhale.erp.hr.domain.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 薪资批次应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PayrollAppServiceImpl implements PayrollAppService {

    /** 期间格式 */
    private static final String PERIOD_PATTERN = "\\d{4}-\\d{2}";

    private final PayrollRepository payrollRepository;

    private final EmployeeRepository employeeRepository;

    private final AttendanceRepository attendanceRepository;

    private final AutoVoucherAppService autoVoucherAppService;

    private final HrPayrollProperties payrollProperties;

    @Override
    public PageResult<PayrollRunView> page(HrQuery query) {
        List<PayrollRunView> records = payrollRepository.page(query).stream()
                .map(PayrollRunView::of).toList();
        return PageResult.of(records, payrollRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public PayrollRunView detail(Long id) {
        return PayrollRunView.of(requireRun(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollRunView create(PayrollCreateCommand command) {
        String period = requirePeriod(command.getPeriod());
        if (payrollRepository.findByPeriod(period).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.PAYROLL_PERIOD_CONFLICT);
        }
        PayrollRun run = new PayrollRun();
        run.setRunNo(payrollRepository.nextRunNo(period));
        run.setPeriod(period);
        run.setStatus(HrStatus.PAYROLL_DRAFT);
        run.setRemark(command.getRemark());
        run.setTotalGross(BigDecimal.ZERO);
        run.setTotalSocialInsurance(BigDecimal.ZERO);
        run.setTotalHousingFund(BigDecimal.ZERO);
        run.setTotalTax(BigDecimal.ZERO);
        run.setTotalNet(BigDecimal.ZERO);
        payrollRepository.insert(run);
        return PayrollRunView.of(run);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollRunView calculate(Long id) {
        PayrollRun run = requireRun(id);
        List<Employee> employees = employeeRepository.listActive();
        Map<String, AttendanceSummary> attendanceMap = new HashMap<>();
        for (AttendanceSummary attendance : attendanceRepository.listByPeriod(run.getPeriod())) {
            attendanceMap.put(attendance.getEmployeeNo(), attendance);
        }
        List<Payslip> payslips = new ArrayList<>();
        for (Employee employee : employees) {
            payslips.add(Payslip.build(employee, attendanceMap.get(employee.getEmployeeNo()), rates()));
        }
        run.calculate(payslips);
        payrollRepository.savePayslips(run);
        payrollRepository.update(run);
        return PayrollRunView.of(run);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollRunView approve(Long id, Long operatorId) {
        PayrollRun run = requireRun(id);
        run.approve();
        AutoVoucherCommand command = new AutoVoucherCommand();
        command.setSourceType(HrStatus.SOURCE_PAYROLL_ACCRUAL);
        command.setSourceId(run.getRunNo());
        command.setVoucherDate(YearMonth.parse(run.getPeriod()).atEndOfMonth());
        command.setAmount(run.getTotalGross());
        command.setOperatorId(operatorId);
        command.setRemark("薪资计提 " + run.getPeriod());
        autoVoucherAppService.postPayrollAccrual(command);
        payrollRepository.update(run);
        return PayrollRunView.of(run);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollRunView pay(Long id, Long operatorId) {
        PayrollRun run = requireRun(id);
        run.pay();
        AutoVoucherCommand command = new AutoVoucherCommand();
        command.setSourceType(HrStatus.SOURCE_PAYROLL_PAYMENT);
        command.setSourceId(run.getRunNo());
        command.setVoucherDate(YearMonth.parse(run.getPeriod()).atEndOfMonth());
        command.setAmount(run.getTotalGross());
        command.setNetAmount(run.getTotalNet());
        command.setTaxAmount(run.getTotalTax());
        command.setWithholdingAmount(run.getTotalSocialInsurance().add(run.getTotalHousingFund()));
        command.setOperatorId(operatorId);
        command.setRemark("薪资发放 " + run.getPeriod());
        autoVoucherAppService.postPayrollPayment(command);
        payrollRepository.update(run);
        return PayrollRunView.of(run);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollRunView close(Long id) {
        PayrollRun run = requireRun(id);
        run.close();
        payrollRepository.update(run);
        return PayrollRunView.of(run);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollRunView cancel(Long id) {
        PayrollRun run = requireRun(id);
        run.cancel();
        payrollRepository.update(run);
        return PayrollRunView.of(run);
    }

    @Override
    public PageResult<PayrollRunView> myPayslips(Long userId, HrQuery query) {
        Employee employee = employeeRepository.findByUserId(userId)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.EMPLOYEE_NOT_FOUND,
                        "当前账号未关联员工档案"));
        List<PayrollRunView> records = payrollRepository.pageByEmployee(employee.getEmployeeNo(), query).stream()
                .map(PayrollRunView::of).toList();
        return PageResult.of(records, payrollRepository.countByEmployee(employee.getEmployeeNo(), query),
                query.getCurrent(), query.getSize());
    }

    /**
     * 查询薪资批次，不存在按 404 处理。
     *
     * @param id 主键
     * @return 薪资批次
     */
    private PayrollRun requireRun(Long id) {
        return payrollRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PAYROLL_NOT_FOUND));
    }

    /**
     * 由配置装配薪资参数。
     *
     * @return 薪资参数
     */
    private HrPayrollRates rates() {
        HrPayrollRates rates = new HrPayrollRates();
        rates.setSocialInsuranceRate(payrollProperties.getSocialInsuranceRate());
        rates.setHousingFundRate(payrollProperties.getHousingFundRate());
        rates.setTaxThreshold(payrollProperties.getTaxThreshold());
        rates.setOvertimeMultiplier(payrollProperties.getOvertimeMultiplier());
        rates.setMonthlyWorkDays(payrollProperties.getMonthlyWorkDays());
        rates.setDailyHours(payrollProperties.getDailyHours());
        return rates;
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
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "薪资期间格式应为 YYYY-MM");
        }
        return value;
    }
}

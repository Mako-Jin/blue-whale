package com.bluewhale.erp.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.hr.application.command.AppraisalSaveCommand;
import com.bluewhale.erp.hr.application.command.KpiCreateCommand;
import com.bluewhale.erp.hr.application.command.PerformanceCycleCreateCommand;
import com.bluewhale.erp.hr.application.service.PerformanceAppService;
import com.bluewhale.erp.hr.application.view.PerformanceCycleView;
import com.bluewhale.erp.hr.domain.model.Appraisal;
import com.bluewhale.erp.hr.domain.model.Employee;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.HrStatus;
import com.bluewhale.erp.hr.domain.model.Kpi;
import com.bluewhale.erp.hr.domain.model.PerformanceCycle;
import com.bluewhale.erp.hr.domain.repository.EmployeeRepository;
import com.bluewhale.erp.hr.domain.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 绩效管理应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PerformanceAppServiceImpl implements PerformanceAppService {

    /** 期间格式 */
    private static final String PERIOD_PATTERN = "\\d{4}-\\d{2}";

    private final PerformanceRepository performanceRepository;

    private final EmployeeRepository employeeRepository;

    @Override
    public PageResult<PerformanceCycleView> page(HrQuery query) {
        List<PerformanceCycleView> records = performanceRepository.page(query).stream()
                .map(cycle -> PerformanceCycleView.of(cycle, null, null)).toList();
        return PageResult.of(records, performanceRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public PerformanceCycleView detail(Long id) {
        PerformanceCycle cycle = requireCycle(id);
        return PerformanceCycleView.of(cycle, performanceRepository.listKpis(id),
                performanceRepository.listAppraisals(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PerformanceCycleView create(PerformanceCycleCreateCommand command) {
        String period = requirePeriod(command.getPeriod());
        PerformanceCycle cycle = new PerformanceCycle();
        cycle.setCycleNo(performanceRepository.nextCycleNo(period));
        cycle.setName(command.getName().trim());
        cycle.setPeriod(period);
        cycle.setStartDate(command.getStartDate());
        cycle.setEndDate(command.getEndDate());
        cycle.setStatus(HrStatus.CYCLE_DRAFT);
        cycle.setRemark(command.getRemark());
        cycle.validate();
        performanceRepository.insert(cycle);
        return PerformanceCycleView.of(cycle, List.of(), List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PerformanceCycleView addKpi(Long id, KpiCreateCommand command) {
        PerformanceCycle cycle = requireCycle(id);
        if (!cycle.appraisable()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_STATE_CONFLICT, "绩效周期已关闭");
        }
        Kpi kpi = new Kpi();
        kpi.setCycleId(cycle.getId());
        kpi.setName(command.getName().trim());
        kpi.setWeight(command.getWeight() == null ? BigDecimal.ZERO : command.getWeight());
        kpi.setTargetValue(command.getTargetValue() == null ? BigDecimal.ZERO : command.getTargetValue());
        kpi.setUnit(command.getUnit());
        kpi.setRemark(command.getRemark());
        kpi.validate();
        performanceRepository.insertKpi(kpi);
        if (HrStatus.CYCLE_DRAFT.equals(cycle.getStatus())) {
            cycle.start();
            performanceRepository.update(cycle);
        }
        return detail(cycle.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PerformanceCycleView appraise(Long id, AppraisalSaveCommand command) {
        PerformanceCycle cycle = requireCycle(id);
        if (!cycle.appraisable()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_STATE_CONFLICT, "绩效周期已关闭");
        }
        Kpi kpi = performanceRepository.findKpi(cycle.getId(), command.getKpiId())
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.KPI_NOT_FOUND));
        Employee employee = employeeRepository.findByEmployeeNo(command.getEmployeeNo())
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.EMPLOYEE_NOT_FOUND));
        Appraisal appraisal = new Appraisal();
        appraisal.setCycleId(cycle.getId());
        appraisal.setKpiId(kpi.getId());
        appraisal.setKpiName(kpi.getName());
        appraisal.setEmployeeNo(employee.getEmployeeNo());
        appraisal.setEmployeeName(employee.getName());
        appraisal.setScore(command.getScore());
        appraisal.setRemark(command.getRemark());
        appraisal.validateAndGrade();
        performanceRepository.upsertAppraisal(appraisal);
        if (HrStatus.CYCLE_DRAFT.equals(cycle.getStatus())) {
            cycle.start();
            performanceRepository.update(cycle);
        }
        return detail(cycle.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PerformanceCycleView close(Long id) {
        PerformanceCycle cycle = requireCycle(id);
        cycle.close();
        performanceRepository.update(cycle);
        return detail(cycle.getId());
    }

    /**
     * 查询绩效周期，不存在按 404 处理。
     *
     * @param id 主键
     * @return 绩效周期
     */
    private PerformanceCycle requireCycle(Long id) {
        return performanceRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PERFORMANCE_CYCLE_NOT_FOUND));
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
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "考核期间格式应为 YYYY-MM");
        }
        return value;
    }
}

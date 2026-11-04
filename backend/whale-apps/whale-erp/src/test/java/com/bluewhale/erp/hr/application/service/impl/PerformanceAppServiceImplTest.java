package com.bluewhale.erp.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.hr.application.command.AppraisalSaveCommand;
import com.bluewhale.erp.hr.application.command.KpiCreateCommand;
import com.bluewhale.erp.hr.application.command.PerformanceCycleCreateCommand;
import com.bluewhale.erp.hr.application.view.PerformanceCycleView;
import com.bluewhale.erp.hr.domain.model.Appraisal;
import com.bluewhale.erp.hr.domain.model.Employee;
import com.bluewhale.erp.hr.domain.model.HrStatus;
import com.bluewhale.erp.hr.domain.model.Kpi;
import com.bluewhale.erp.hr.domain.model.PerformanceCycle;
import com.bluewhale.erp.hr.domain.repository.EmployeeRepository;
import com.bluewhale.erp.hr.domain.repository.PerformanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PerformanceAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class PerformanceAppServiceImplTest {

    private final PerformanceRepository performanceRepository = mock(PerformanceRepository.class);

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);

    private final PerformanceAppServiceImpl service = new PerformanceAppServiceImpl(performanceRepository,
            employeeRepository);

    @Test
    @DisplayName("新建绩效周期生成编号并置草稿")
    void shouldCreateCycle() {
        when(performanceRepository.nextCycleNo("2026-11")).thenReturn("PC-202611-0001");

        PerformanceCycleView view = service.create(cycleCommand());

        assertEquals("PC-202611-0001", view.getCycleNo());
        assertEquals(HrStatus.CYCLE_DRAFT, view.getStatus());
        verify(performanceRepository).insert(any(PerformanceCycle.class));
    }

    @Test
    @DisplayName("新增指标后草稿周期自动启动")
    void shouldAddKpiAndStartCycle() {
        PerformanceCycle cycle = cycle(HrStatus.CYCLE_DRAFT);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(cycle));
        when(performanceRepository.listKpis(1L)).thenReturn(List.of(kpi()));
        when(performanceRepository.listAppraisals(1L)).thenReturn(List.of());

        PerformanceCycleView view = service.addKpi(1L, kpiCommand());

        assertEquals(HrStatus.CYCLE_IN_PROGRESS, view.getStatus());
        assertEquals(1, view.getKpis().size());
        verify(performanceRepository).insertKpi(any(Kpi.class));
        verify(performanceRepository).update(any(PerformanceCycle.class));
    }

    @Test
    @DisplayName("录入考核结果并按得分推导等级")
    void shouldAppraise() {
        PerformanceCycle cycle = cycle(HrStatus.CYCLE_IN_PROGRESS);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(cycle));
        when(performanceRepository.findKpi(1L, 2L)).thenReturn(Optional.of(kpi()));
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.of(employee()));
        when(performanceRepository.listKpis(1L)).thenReturn(List.of(kpi()));
        Appraisal saved = new Appraisal();
        saved.setId(9L);
        saved.setKpiId(2L);
        saved.setKpiName("交付及时率");
        saved.setEmployeeNo("E001");
        saved.setEmployeeName("张三");
        saved.setScore(new BigDecimal("86"));
        saved.setGrade("B");
        when(performanceRepository.listAppraisals(1L)).thenReturn(List.of(saved));

        PerformanceCycleView view = service.appraise(1L, appraisalCommand());

        assertEquals(1, view.getAppraisals().size());
        assertEquals("B", view.getAppraisals().get(0).getGrade());
        verify(performanceRepository).upsertAppraisal(any(Appraisal.class));
    }

    @Test
    @DisplayName("指标不属于周期时拒绝录入")
    void shouldRejectMissingKpi() {
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(cycle(HrStatus.CYCLE_IN_PROGRESS)));
        when(performanceRepository.findKpi(1L, 2L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.appraise(1L, appraisalCommand()));

        assertEquals(ErpErrorCode.KPI_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("周期关闭后拒绝录入")
    void shouldRejectAppraiseOnClosedCycle() {
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(cycle(HrStatus.CYCLE_CLOSED)));

        BizException exception = assertThrows(BizException.class, () -> service.addKpi(1L, kpiCommand()));

        assertEquals(ErpErrorCode.HR_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("关闭绩效周期")
    void shouldCloseCycle() {
        PerformanceCycle cycle = cycle(HrStatus.CYCLE_IN_PROGRESS);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(cycle));
        when(performanceRepository.listKpis(1L)).thenReturn(List.of());
        when(performanceRepository.listAppraisals(1L)).thenReturn(List.of());

        PerformanceCycleView view = service.close(1L);

        assertEquals(HrStatus.CYCLE_CLOSED, view.getStatus());
        verify(performanceRepository).update(any(PerformanceCycle.class));
    }

    @Test
    @DisplayName("周期不存在按 404 处理")
    void shouldRejectMissingCycle() {
        when(performanceRepository.findById(1L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.detail(1L));

        assertEquals(ErpErrorCode.PERFORMANCE_CYCLE_NOT_FOUND, exception.getCode());
    }

    /**
     * 构造新建周期命令。
     *
     * @return 命令
     */
    private PerformanceCycleCreateCommand cycleCommand() {
        PerformanceCycleCreateCommand command = new PerformanceCycleCreateCommand();
        command.setName("2026 年 11 月绩效");
        command.setPeriod("2026-11");
        command.setStartDate(LocalDate.of(2026, 11, 1));
        command.setEndDate(LocalDate.of(2026, 11, 30));
        return command;
    }

    /**
     * 构造指标命令。
     *
     * @return 命令
     */
    private KpiCreateCommand kpiCommand() {
        KpiCreateCommand command = new KpiCreateCommand();
        command.setName("交付及时率");
        command.setWeight(new BigDecimal("30"));
        command.setTargetValue(new BigDecimal("95"));
        command.setUnit("%");
        return command;
    }

    /**
     * 构造考核命令。
     *
     * @return 命令
     */
    private AppraisalSaveCommand appraisalCommand() {
        AppraisalSaveCommand command = new AppraisalSaveCommand();
        command.setEmployeeNo("E001");
        command.setKpiId(2L);
        command.setScore(new BigDecimal("86"));
        return command;
    }

    /**
     * 构造绩效周期。
     *
     * @param status 状态
     * @return 周期
     */
    private PerformanceCycle cycle(String status) {
        PerformanceCycle cycle = new PerformanceCycle();
        cycle.setId(1L);
        cycle.setCycleNo("PC-202611-0001");
        cycle.setName("2026 年 11 月绩效");
        cycle.setPeriod("2026-11");
        cycle.setStartDate(LocalDate.of(2026, 11, 1));
        cycle.setEndDate(LocalDate.of(2026, 11, 30));
        cycle.setStatus(status);
        return cycle;
    }

    /**
     * 构造指标。
     *
     * @return 指标
     */
    private Kpi kpi() {
        Kpi kpi = new Kpi();
        kpi.setId(2L);
        kpi.setCycleId(1L);
        kpi.setName("交付及时率");
        kpi.setWeight(new BigDecimal("30"));
        kpi.setTargetValue(new BigDecimal("95"));
        kpi.setUnit("%");
        return kpi;
    }

    /**
     * 构造员工。
     *
     * @return 员工
     */
    private Employee employee() {
        Employee employee = new Employee();
        employee.setId(3L);
        employee.setEmployeeNo("E001");
        employee.setName("张三");
        employee.setOrgCode("ORG-01");
        employee.setHireDate(LocalDate.of(2026, 1, 1));
        employee.setStatus(HrStatus.EMPLOYEE_ACTIVE);
        return employee;
    }
}

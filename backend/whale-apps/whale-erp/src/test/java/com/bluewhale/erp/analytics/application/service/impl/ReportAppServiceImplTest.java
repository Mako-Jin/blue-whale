package com.bluewhale.erp.analytics.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.analytics.application.command.ReportDefinitionSaveCommand;
import com.bluewhale.erp.analytics.application.command.ReportScheduleSaveCommand;
import com.bluewhale.erp.analytics.application.service.AnalyticsAppService;
import com.bluewhale.erp.analytics.application.view.CubeResultView;
import com.bluewhale.erp.analytics.application.view.CubeRowView;
import com.bluewhale.erp.analytics.application.view.ReportDefinitionView;
import com.bluewhale.erp.analytics.application.view.ReportScheduleView;
import com.bluewhale.erp.analytics.domain.model.AnalyticsStatus;
import com.bluewhale.erp.analytics.domain.model.ReportDefinition;
import com.bluewhale.erp.analytics.domain.model.ReportSchedule;
import com.bluewhale.erp.analytics.domain.repository.ReportRepository;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReportAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class ReportAppServiceImplTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);

    private final AnalyticsAppService analyticsAppService = mock(AnalyticsAppService.class);

    private final ReportAppServiceImpl service =
            new ReportAppServiceImpl(reportRepository, analyticsAppService);

    @Test
    @DisplayName("报表编号重复拒绝新建")
    void shouldRejectDuplicateReport() {
        when(reportRepository.findDefinitionByNo("RPT-001"))
                .thenReturn(Optional.of(definition("RPT-001")));

        BizException exception = assertThrows(BizException.class,
                () -> service.createDefinition(definitionCommand("RPT-001")));

        assertEquals(ErpErrorCode.REPORT_NO_CONFLICT, exception.getCode());
        verify(reportRepository, never()).insertDefinition(any(ReportDefinition.class));
    }

    @Test
    @DisplayName("新建报表定义成功")
    void shouldCreateReport() {
        when(reportRepository.findDefinitionByNo("RPT-002")).thenReturn(Optional.empty());

        ReportDefinitionView view = service.createDefinition(definitionCommand("RPT-002"));

        assertEquals("RPT-002", view.getReportNo());
        assertEquals(AnalyticsStatus.ENABLED, view.getStatus());
        verify(reportRepository).insertDefinition(any(ReportDefinition.class));
    }

    @Test
    @DisplayName("编辑不存在的报表返回 404")
    void shouldRejectUpdateMissingReport() {
        when(reportRepository.findDefinitionByNo("RPT-404")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.updateDefinition("RPT-404", definitionCommand("RPT-404")));

        assertEquals(ErpErrorCode.REPORT_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("执行报表委托多维分析")
    void shouldExecuteReport() {
        when(reportRepository.findDefinitionByNo("RPT-001"))
                .thenReturn(Optional.of(definition("RPT-001")));
        CubeResultView cube = new CubeResultView();
        cube.setMetricType(AnalyticsStatus.METRIC_REVENUE);
        cube.setRows(List.of());
        when(analyticsAppService.cube(any())).thenReturn(cube);

        CubeResultView result = service.execute("RPT-001", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 4));

        assertEquals(AnalyticsStatus.METRIC_REVENUE, result.getMetricType());
        verify(analyticsAppService).cube(any());
    }

    @Test
    @DisplayName("导出 CSV 含表头与维度行")
    void shouldExportCsv() {
        when(reportRepository.findDefinitionByNo("RPT-001"))
                .thenReturn(Optional.of(definition("RPT-001")));
        CubeRowView row = new CubeRowView();
        row.setDimensionKey("C001");
        row.setDimensionName("客户一");
        row.setMetricValue(new BigDecimal("100"));
        row.setOrderCount(2);
        CubeResultView cube = new CubeResultView();
        cube.setMetricType(AnalyticsStatus.METRIC_REVENUE);
        cube.setRows(List.of(row));
        when(analyticsAppService.cube(any())).thenReturn(cube);

        String csv = service.exportCsv("RPT-001", null, null);

        assertTrue(csv.contains("维度键,维度名称"));
        assertTrue(csv.contains("\"客户一\""));
        assertTrue(csv.contains("REVENUE"));
    }

    @Test
    @DisplayName("执行报表计划记录执行时间")
    void shouldRunSchedule() {
        ReportSchedule schedule = new ReportSchedule();
        schedule.setScheduleNo("SCH-001");
        schedule.setReportNo("RPT-001");
        schedule.setScheduleName("日报");
        schedule.setCronExpression("0 8 * * *");
        schedule.setStatus(AnalyticsStatus.ENABLED);
        when(reportRepository.findScheduleByNo("SCH-001")).thenReturn(Optional.of(schedule));
        when(reportRepository.findDefinitionByNo("RPT-001"))
                .thenReturn(Optional.of(definition("RPT-001")));

        ReportScheduleView view = service.runSchedule("SCH-001");

        assertNotNull(view.getLastRunTime());
        assertNotNull(view.getNextRunTime());
        verify(reportRepository).updateSchedule(schedule);
    }

    @Test
    @DisplayName("计划引用不存在的报表拒绝新建")
    void shouldRejectScheduleWithMissingReport() {
        when(reportRepository.findScheduleByNo("SCH-002")).thenReturn(Optional.empty());
        when(reportRepository.findDefinitionByNo("RPT-999")).thenReturn(Optional.empty());

        ReportScheduleSaveCommand command = new ReportScheduleSaveCommand();
        command.setScheduleNo("SCH-002");
        command.setScheduleName("周报");
        command.setReportNo("RPT-999");
        command.setCronExpression("0 8 * * 1");

        BizException exception = assertThrows(BizException.class,
                () -> service.createSchedule(command));

        assertEquals(ErpErrorCode.REPORT_NOT_FOUND, exception.getCode());
        verify(reportRepository, never()).insertSchedule(any(ReportSchedule.class));
    }

    /**
     * 构造报表定义。
     *
     * @param reportNo 编号
     * @return 定义
     */
    private ReportDefinition definition(String reportNo) {
        ReportDefinition definition = new ReportDefinition();
        definition.setId(1L);
        definition.setReportNo(reportNo);
        definition.setReportName("销售分析");
        definition.setReportType("SALES");
        definition.setDimensionType(AnalyticsStatus.DIMENSION_CUSTOMER);
        definition.setMetricType(AnalyticsStatus.METRIC_REVENUE);
        definition.setStatus(AnalyticsStatus.ENABLED);
        return definition;
    }

    /**
     * 构造报表保存命令。
     *
     * @param reportNo 编号
     * @return 命令
     */
    private ReportDefinitionSaveCommand definitionCommand(String reportNo) {
        ReportDefinitionSaveCommand command = new ReportDefinitionSaveCommand();
        command.setReportNo(reportNo);
        command.setReportName("销售分析");
        command.setReportType("SALES");
        command.setDimensionType(AnalyticsStatus.DIMENSION_CUSTOMER);
        command.setMetricType(AnalyticsStatus.METRIC_REVENUE);
        return command;
    }
}

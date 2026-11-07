package com.bluewhale.oa.analytics.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.analytics.application.query.ReportQuery;
import com.bluewhale.oa.analytics.application.view.ReportViews;
import com.bluewhale.oa.analytics.domain.model.ReportExportLog;
import com.bluewhale.oa.analytics.domain.model.ReportRows;
import com.bluewhale.oa.analytics.domain.model.ReportSnapshot;
import com.bluewhale.oa.analytics.domain.repository.ReportRepository;
import com.bluewhale.oa.common.OaErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReportAppServiceImpl} 报表聚合、快照与导出测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ReportAppServiceImplTest {

    @Mock
    private ReportRepository reportRepository;

    private ObjectMapper objectMapper;

    private ReportAppServiceImpl reportAppService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        reportAppService = new ReportAppServiceImpl(reportRepository, objectMapper, 2048L);
    }

    @Test
    @DisplayName("报表目录应包含 9 类报表且每类都有列定义")
    void catalogShouldContainAllReports() {
        List<ReportViews.CatalogItem> catalog = reportAppService.catalog();
        assertEquals(9, catalog.size(), "报表目录应覆盖 9 类报表");
        assertTrue(catalog.stream().allMatch(item -> !item.getColumns().isEmpty()),
                "每类报表都应有列定义");
    }

    @Test
    @DisplayName("考勤月报应按仓库结果转换为统一行列结构")
    void attendanceReportShouldConvertRows() {
        ReportRows.AttendanceRow row = new ReportRows.AttendanceRow();
        row.setUserId(1001L);
        row.setUserName("张三");
        row.setWorkDays(20);
        row.setLateDays(1);
        row.setEarlyDays(0);
        row.setAbsentDays(1);
        row.setLateMinutes(12);
        row.setEarlyMinutes(0);
        when(reportRepository.attendanceMonthly(any(), any())).thenReturn(List.of(row));

        ReportQuery query = new ReportQuery();
        query.setFrom(LocalDate.of(2026, 10, 1));
        query.setTo(LocalDate.of(2026, 10, 31));
        ReportViews.ReportResult result = reportAppService.run("ATTENDANCE_MONTHLY", query);

        assertEquals(1, result.getRowCount());
        assertEquals("考勤月报", result.getTitle());
        assertEquals(8, result.getColumns().size());
        Map<String, Object> data = result.getRows().get(0);
        assertEquals("张三", data.get("userName"));
        assertEquals(1, data.get("absentDays"));
    }

    @Test
    @DisplayName("未知报表编码与倒置日期区间应拒绝")
    void invalidArgumentsShouldBeRejected() {
        ReportQuery query = new ReportQuery();
        BizException unknown = assertThrows(BizException.class, () -> reportAppService.run("UNKNOWN", query));
        assertEquals(OaErrorCode.REPORT_INVALID, unknown.getCode());

        ReportQuery reversed = new ReportQuery();
        reversed.setFrom(LocalDate.of(2026, 10, 31));
        reversed.setTo(LocalDate.of(2026, 10, 1));
        BizException invalid = assertThrows(BizException.class,
                () -> reportAppService.run("ATTENDANCE_MONTHLY", reversed));
        assertEquals(OaErrorCode.REPORT_INVALID, invalid.getCode());
    }

    @Test
    @DisplayName("审批效率应按业务类型计算平均、P50、P90 与超 48 小时积压")
    void approvalReportShouldAggregateDurations() {
        LocalDateTime now = LocalDateTime.now();
        ReportRows.ApprovalRow approvedFast = approvalRow("LEAVE", "APPROVED", now.minusMinutes(70),
                now.minusMinutes(10));
        ReportRows.ApprovalRow approvedSlow = approvalRow("LEAVE", "APPROVED", now.minusMinutes(180),
                now.minusMinutes(60));
        ReportRows.ApprovalRow running = approvalRow("LEAVE", "RUNNING", now.minusHours(72), null);
        when(reportRepository.approvalInstances(any(), any()))
                .thenReturn(List.of(approvedFast, approvedSlow, running));

        ReportQuery query = new ReportQuery();
        query.setFrom(LocalDate.of(2026, 10, 1));
        query.setTo(LocalDate.of(2026, 10, 31));
        ReportViews.ReportResult result = reportAppService.run("APPROVAL_EFFICIENCY", query);

        Map<String, Object> row = result.getRows().get(0);
        assertEquals("LEAVE", row.get("businessType"));
        assertEquals(3, row.get("totalCount"));
        assertEquals(2, row.get("approvedCount"));
        assertEquals(1, row.get("runningCount"));
        assertEquals(1, row.get("overdue48Count"));
        assertEquals(90L, row.get("avgMinutes"));
        assertEquals(60L, row.get("p50Minutes"));
        assertEquals(120L, row.get("p90Minutes"));
    }

    @Test
    @DisplayName("生成快照应保存聚合 JSON 并返回元信息")
    void createSnapshotShouldStorePayload() {
        when(reportRepository.attendanceMonthly(any(), any())).thenReturn(List.of());
        when(reportRepository.findSnapshot(any(), any())).thenReturn(Optional.empty());

        ReportViews.SnapshotView view = reportAppService.createSnapshot("ATTENDANCE_MONTHLY", "2026-10",
                7L, "管理员");

        ArgumentCaptor<ReportSnapshot> captor = ArgumentCaptor.forClass(ReportSnapshot.class);
        verify(reportRepository).saveSnapshot(captor.capture());
        ReportSnapshot saved = captor.getValue();
        assertEquals("2026-10", saved.getPeriod());
        assertEquals(LocalDate.of(2026, 10, 1), saved.getFromDate());
        assertEquals(LocalDate.of(2026, 10, 31), saved.getToDate());
        assertTrue(saved.getPayload().contains("ATTENDANCE_MONTHLY"));
        assertEquals("考勤月报", saved.getReportName());
        assertEquals(7L, view.getGeneratedBy());
    }

    @Test
    @DisplayName("读取不存在的快照应返回 40442")
    void missingSnapshotShouldBeRejected() {
        when(reportRepository.findSnapshotById(anyLong())).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class, () -> reportAppService.snapshotDetail(1L));
        assertEquals(OaErrorCode.REPORT_SNAPSHOT_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("导出应生成带 BOM 的 CSV、转义逗号并登记导出记录")
    void exportShouldWriteCsvAndLog() {
        ReportRows.AttendanceRow row = new ReportRows.AttendanceRow();
        row.setUserId(1001L);
        row.setUserName("张,三");
        row.setWorkDays(20);
        row.setLateDays(0);
        row.setEarlyDays(0);
        row.setAbsentDays(0);
        row.setLateMinutes(0);
        row.setEarlyMinutes(0);
        when(reportRepository.attendanceMonthly(any(), any())).thenReturn(List.of(row));

        ReportQuery query = new ReportQuery();
        query.setFrom(LocalDate.of(2026, 10, 1));
        query.setTo(LocalDate.of(2026, 10, 31));
        ReportViews.ExportFile file = reportAppService.export("ATTENDANCE_MONTHLY", query, 7L, "管理员");

        assertTrue(file.getFilename().endsWith(".csv"));
        String content = new String(file.getContent(), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("\uFEFF"), "CSV 应带 UTF-8 BOM");
        assertTrue(content.contains("\"张,三\""), "含逗号的单元格应被引号包裹");
        assertTrue(content.contains("用户ID"));

        ArgumentCaptor<ReportExportLog> captor = ArgumentCaptor.forClass(ReportExportLog.class);
        verify(reportRepository).saveExportLog(captor.capture());
        assertEquals("CSV", captor.getValue().getExportFormat());
        assertEquals(1, captor.getValue().getRowCount());
    }

    /**
     * 构造审批实例行。
     *
     * @param businessType 业务类型
     * @param status 状态
     * @param submittedAt 提交时间
     * @param finishedAt 完成时间
     * @return 审批实例行
     */
    private static ReportRows.ApprovalRow approvalRow(String businessType, String status,
            LocalDateTime submittedAt, LocalDateTime finishedAt) {
        ReportRows.ApprovalRow row = new ReportRows.ApprovalRow();
        row.setBusinessType(businessType);
        row.setStatus(status);
        row.setSubmittedAt(submittedAt);
        row.setFinishedAt(finishedAt);
        return row;
    }
}

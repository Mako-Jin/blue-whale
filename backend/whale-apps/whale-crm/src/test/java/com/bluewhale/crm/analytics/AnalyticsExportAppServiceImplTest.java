package com.bluewhale.crm.analytics;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.crm.analytics.application.service.AnalyticsExportAppService;
import com.bluewhale.crm.analytics.application.service.impl.AnalyticsExportAppServiceImpl;
import com.bluewhale.crm.analytics.domain.model.AnalyticsExport;
import com.bluewhale.crm.analytics.domain.model.ExportStatus;
import com.bluewhale.crm.analytics.domain.model.ExportTable;
import com.bluewhale.crm.analytics.domain.model.ExportType;
import com.bluewhale.crm.analytics.domain.port.AnalyticsFilePort;
import com.bluewhale.crm.analytics.domain.port.AnalyticsSourcePort;
import com.bluewhale.crm.analytics.domain.repository.AnalyticsExportRepository;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmAnalyticsProperties;
import com.bluewhale.crm.config.CrmProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AnalyticsExportAppServiceImpl} 导出与脱敏单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsExportAppServiceImplTest {

    /** 单次导出行数上限 */
    private static final int MAX_ROWS = 10;

    @Mock
    private AnalyticsExportRepository analyticsExportRepository;

    @Mock
    private AnalyticsSourcePort analyticsSourcePort;

    @Mock
    private AnalyticsFilePort analyticsFilePort;

    private CrmProperties crmProperties;

    private CrmAnalyticsProperties analyticsProperties;

    private AnalyticsExportAppServiceImpl service;

    @BeforeEach
    void setUp() {
        crmProperties = new CrmProperties();
        analyticsProperties = new CrmAnalyticsProperties();
        analyticsProperties.setExportMaxRows(MAX_ROWS);
        service = new AnalyticsExportAppServiceImpl(analyticsExportRepository, analyticsSourcePort,
                analyticsFilePort, crmProperties, analyticsProperties);
    }

    @Test
    @DisplayName("导出：超过单次行数上限直接拒绝且不落库")
    void shouldRejectWhenOverMaxRows() {
        when(analyticsSourcePort.exportTable(eq(ExportType.CUSTOMER), any(), eq(MAX_ROWS)))
                .thenReturn(table(MAX_ROWS + 1));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(ExportType.CUSTOMER, null, adminActor()));

        assertEquals(CrmErrorCode.CUSTOMER_EXPORT_INVALID, exception.getCode());
        verify(analyticsExportRepository, never()).insert(any(AnalyticsExport.class));
    }

    @Test
    @DisplayName("导出：CSV 含 BOM 并按表头位置脱敏手机号与邮箱，产物上传 whale-file")
    void shouldMaskPhoneAndEmailInCsv() {
        ExportTable table = table(1);
        table.setHeaders(List.of("客户名称", "手机号", "邮箱"));
        table.setRows(List.of(List.of("客户A", "13800001111", "zhangsan@example.com")));
        when(analyticsSourcePort.exportTable(eq(ExportType.CUSTOMER), any(), eq(MAX_ROWS))).thenReturn(table);
        when(analyticsExportRepository.countByPrefix(anyString())).thenReturn(0L);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        when(analyticsFilePort.upload(bytesCaptor.capture(), anyString(), eq(1L))).thenReturn(9001L);

        AnalyticsExport export = service.create(ExportType.CUSTOMER, null, adminActor());

        assertEquals(ExportStatus.COMPLETED, export.getStatus());
        assertEquals(1, export.getRowCount());
        assertTrue(export.getExportNo().startsWith("EXP-"));
        assertTrue(export.getExportNo().endsWith("-0001"));
        String csv = new String(bytesCaptor.getValue(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("138****1111"));
        assertTrue(csv.contains("z****@example.com"));
        assertFalse(csv.contains("13800001111"));
        assertFalse(csv.contains("zhangsan@example.com"));
        verify(analyticsExportRepository).insert(export);
    }

    @Test
    @DisplayName("导出内容：任务未完成时返回 EXPORT_TASK_FAILED")
    void shouldRejectContentOfUnfinishedExport() {
        AnalyticsExport export = new AnalyticsExport();
        export.setId(1L);
        export.setStatus(ExportStatus.RUNNING);
        export.setRequestedBy(1L);
        when(analyticsExportRepository.findById(1L)).thenReturn(export);

        BizException exception = assertThrows(BizException.class,
                () -> service.content(1L, adminActor()));

        assertEquals(CrmErrorCode.EXPORT_TASK_FAILED, exception.getCode());
    }

    @Test
    @DisplayName("导出：产物上传 whale-file，仅落 fileId/fileName/fileSize 引用")
    void shouldUploadToWhaleFileAndKeepReference() {
        ExportTable table = table(2);
        when(analyticsSourcePort.exportTable(eq(ExportType.CUSTOMER), any(), eq(MAX_ROWS))).thenReturn(table);
        when(analyticsExportRepository.countByPrefix(anyString())).thenReturn(0L);
        when(analyticsFilePort.upload(any(byte[].class), anyString(), eq(1L))).thenReturn(9001L);

        AnalyticsExport export = service.create(ExportType.CUSTOMER, null, adminActor());

        assertEquals(ExportStatus.COMPLETED, export.getStatus());
        assertEquals(9001L, export.getFileId());
        assertEquals(2, export.getRowCount());
        assertTrue(export.getFileName().endsWith(".csv"));
        assertTrue(export.getFileSize() > 0);
        assertNull(export.getContent());
        verify(analyticsExportRepository).insert(export);
    }

    @Test
    @DisplayName("导出：文件服务不可用时任务置失败并拒绝")
    void shouldFailWhenFileServiceUnavailable() {
        when(analyticsSourcePort.exportTable(eq(ExportType.CUSTOMER), any(), eq(MAX_ROWS)))
                .thenReturn(table(1));
        when(analyticsExportRepository.countByPrefix(anyString())).thenReturn(0L);
        when(analyticsFilePort.upload(any(byte[].class), anyString(), eq(1L)))
                .thenThrow(new BizException(ErrorCode.SYSTEM_ERROR, "上传导出文件失败"));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(ExportType.CUSTOMER, null, adminActor()));

        assertEquals(CrmErrorCode.EXPORT_TASK_FAILED, exception.getCode());
        verify(analyticsExportRepository).insert(any(AnalyticsExport.class));
    }

    @Test
    @DisplayName("代理下载：新导出回读 whale-file，历史导出回退落库内容")
    void shouldDownloadViaFilePortOrLegacyContent() {
        AnalyticsExport fileBacked = new AnalyticsExport();
        fileBacked.setId(1L);
        fileBacked.setStatus(ExportStatus.COMPLETED);
        fileBacked.setRequestedBy(1L);
        fileBacked.setFileId(9001L);
        fileBacked.setFileName("EXP-20261108-0001.csv");
        when(analyticsExportRepository.findById(1L)).thenReturn(fileBacked);
        when(analyticsFilePort.download(9001L)).thenReturn("a,b\r\n".getBytes(StandardCharsets.UTF_8));

        AnalyticsExportAppService.ExportDownload download = service.download(1L, adminActor());
        assertEquals("EXP-20261108-0001.csv", download.getFileName());
        assertTrue(download.getContent().length > 0);

        AnalyticsExport legacy = new AnalyticsExport();
        legacy.setId(2L);
        legacy.setStatus(ExportStatus.COMPLETED);
        legacy.setRequestedBy(1L);
        legacy.setContent("﻿a,b\r\n");
        when(analyticsExportRepository.findById(2L)).thenReturn(legacy);

        AnalyticsExportAppService.ExportDownload legacyDownload = service.download(2L, adminActor());
        assertTrue(legacyDownload.getContent().length > 0);
        verify(analyticsFilePort, never()).download(eq(2L));
    }

    /**
     * 构造导出数据表。
     *
     * @param rowCount 数据行数
     * @return 导出数据表
     */
    private ExportTable table(int rowCount) {
        ExportTable table = new ExportTable();
        table.setHeaders(List.of("客户名称"));
        table.setRows(List.of());
        table.setRowCount(rowCount);
        return table;
    }

    /**
     * 构造管理员操作人。
     *
     * @return 操作人
     */
    private CrmActor adminActor() {
        return new CrmActor(1L, "admin", Set.of("tenant_admin"), Set.of());
    }
}

package com.bluewhale.crm.analytics;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.analytics.application.service.impl.AnalyticsExportAppServiceImpl;
import com.bluewhale.crm.analytics.domain.model.AnalyticsExport;
import com.bluewhale.crm.analytics.domain.model.ExportStatus;
import com.bluewhale.crm.analytics.domain.model.ExportTable;
import com.bluewhale.crm.analytics.domain.model.ExportType;
import com.bluewhale.crm.analytics.domain.port.AnalyticsExportFilePort;
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
    private AnalyticsExportFilePort analyticsExportFilePort;

    private CrmProperties crmProperties;

    private CrmAnalyticsProperties analyticsProperties;

    private AnalyticsExportAppServiceImpl service;

    @BeforeEach
    void setUp() {
        crmProperties = new CrmProperties();
        analyticsProperties = new CrmAnalyticsProperties();
        analyticsProperties.setExportMaxRows(MAX_ROWS);
        service = new AnalyticsExportAppServiceImpl(analyticsExportRepository, analyticsSourcePort,
                analyticsExportFilePort, crmProperties, analyticsProperties);
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
    @DisplayName("导出：CSV 上传 whale-file，落文件引用且含 BOM 与手机号邮箱脱敏")
    void shouldUploadCsvWithMaskedFields() {
        ExportTable table = table(1);
        table.setHeaders(List.of("客户名称", "手机号", "邮箱"));
        table.setRows(List.of(List.of("客户A", "13800001111", "zhangsan@example.com")));
        when(analyticsSourcePort.exportTable(eq(ExportType.CUSTOMER), any(), eq(MAX_ROWS))).thenReturn(table);
        when(analyticsExportRepository.countByPrefix(anyString())).thenReturn(0L);
        when(analyticsExportFilePort.upload(anyString(), any(byte[].class), eq(1L), eq(1L))).thenReturn(9001L);

        AnalyticsExport export = service.create(ExportType.CUSTOMER, null, adminActor());

        assertEquals(ExportStatus.COMPLETED, export.getStatus());
        assertEquals(1, export.getRowCount());
        assertTrue(export.getExportNo().startsWith("EXP-"));
        assertTrue(export.getExportNo().endsWith("-0001"));
        assertEquals(Long.valueOf(9001L), export.getFileId());
        assertEquals(export.getExportNo() + ".csv", export.getFileName());

        ArgumentCaptor<byte[]> content = ArgumentCaptor.forClass(byte[].class);
        verify(analyticsExportFilePort).upload(eq(export.getFileName()), content.capture(), eq(1L), eq(1L));
        String csv = new String(content.getValue(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("138****1111"));
        assertTrue(csv.contains("z****@example.com"));
        assertFalse(csv.contains("13800001111"));
        assertFalse(csv.contains("zhangsan@example.com"));
        verify(analyticsExportRepository).insert(export);
    }

    @Test
    @DisplayName("导出：文件上传失败时任务置失败并抛 EXPORT_TASK_FAILED")
    void shouldMarkFailedWhenUploadFails() {
        when(analyticsSourcePort.exportTable(eq(ExportType.CUSTOMER), any(), eq(MAX_ROWS)))
                .thenReturn(table(1));
        when(analyticsExportRepository.countByPrefix(anyString())).thenReturn(0L);
        when(analyticsExportFilePort.upload(anyString(), any(byte[].class), eq(1L), eq(1L)))
                .thenThrow(CrmErrorCode.exception(CrmErrorCode.EXPORT_TASK_FAILED, "文件服务不可用"));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(ExportType.CUSTOMER, null, adminActor()));

        assertEquals(CrmErrorCode.EXPORT_TASK_FAILED, exception.getCode());
        ArgumentCaptor<AnalyticsExport> saved = ArgumentCaptor.forClass(AnalyticsExport.class);
        verify(analyticsExportRepository).insert(saved.capture());
        assertEquals(ExportStatus.FAILED, saved.getValue().getStatus());
        assertTrue(saved.getValue().getError().contains("文件服务不可用"));
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

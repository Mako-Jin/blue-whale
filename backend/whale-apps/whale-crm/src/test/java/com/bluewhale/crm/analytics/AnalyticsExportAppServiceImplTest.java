package com.bluewhale.crm.analytics;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.analytics.application.service.impl.AnalyticsExportAppServiceImpl;
import com.bluewhale.crm.analytics.domain.model.AnalyticsExport;
import com.bluewhale.crm.analytics.domain.model.ExportStatus;
import com.bluewhale.crm.analytics.domain.model.ExportTable;
import com.bluewhale.crm.analytics.domain.model.ExportType;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private CrmProperties crmProperties;

    private CrmAnalyticsProperties analyticsProperties;

    private AnalyticsExportAppServiceImpl service;

    @BeforeEach
    void setUp() {
        crmProperties = new CrmProperties();
        analyticsProperties = new CrmAnalyticsProperties();
        analyticsProperties.setExportMaxRows(MAX_ROWS);
        service = new AnalyticsExportAppServiceImpl(analyticsExportRepository, analyticsSourcePort,
                crmProperties, analyticsProperties);
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
    @DisplayName("导出：CSV 含 BOM 并按表头位置脱敏手机号与邮箱")
    void shouldMaskPhoneAndEmailInCsv() {
        ExportTable table = table(1);
        table.setHeaders(List.of("客户名称", "手机号", "邮箱"));
        table.setRows(List.of(List.of("客户A", "13800001111", "zhangsan@example.com")));
        when(analyticsSourcePort.exportTable(eq(ExportType.CUSTOMER), any(), eq(MAX_ROWS))).thenReturn(table);
        when(analyticsExportRepository.countByPrefix(anyString())).thenReturn(0L);

        AnalyticsExport export = service.create(ExportType.CUSTOMER, null, adminActor());

        assertEquals(ExportStatus.COMPLETED, export.getStatus());
        assertEquals(1, export.getRowCount());
        assertTrue(export.getExportNo().startsWith("EXP-"));
        assertTrue(export.getExportNo().endsWith("-0001"));
        assertTrue(export.getContent().startsWith("\uFEFF"));
        assertTrue(export.getContent().contains("138****1111"));
        assertTrue(export.getContent().contains("z****@example.com"));
        assertFalse(export.getContent().contains("13800001111"));
        assertFalse(export.getContent().contains("zhangsan@example.com"));
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

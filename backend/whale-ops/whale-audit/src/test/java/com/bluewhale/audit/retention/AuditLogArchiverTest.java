package com.bluewhale.audit.retention;

import com.bluewhale.audit.config.AuditExportProperties;
import com.bluewhale.audit.support.AuditArchiveClient;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@code audit_log} 归档任务单测（AUTH-09）：过期记录导出上传、无记录跳过、
 * 上传失败受控、<b>任何路径下均不调用 {@code audit_log} 删除 Mapper 方法</b>。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuditLogArchiverTest {

    /** 测试用保留天数 */
    private static final int RETENTION_DAYS = 30;

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private AuditArchiveClient archiveClient;

    private final AuditExportProperties exportProperties = new AuditExportProperties();

    @Test
    void shouldUploadExpiredRecordsAndNeverDeleteAuditLog() {
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());
        Map<String, Object> uploaded = new HashMap<>();
        uploaded.put("objectKey", "bluewhale-audit-archive/2026/auditLog-archive-20261019.csv");
        doReturn(uploaded).when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver = new AuditLogArchiver(auditLogMapper, archiveClient, exportProperties);

        archiver.archive(RETENTION_DAYS);

        verify(archiveClient).upload(any(), any(), any());
        verify(auditLogMapper, never()).delete(any());
        verify(auditLogMapper, never()).deleteById(any());
        verify(auditLogMapper, never()).deleteByIds(any());
        verify(auditLogMapper, never()).updateById(any(AuditLogRecord.class));
    }

    @Test
    void shouldSkipUploadWhenNoExpiredRecords() {
        doReturn(List.of()).when(auditLogMapper).selectList(any());
        AuditLogArchiver archiver = new AuditLogArchiver(auditLogMapper, archiveClient, exportProperties);

        archiver.archive(RETENTION_DAYS);

        verifyNoInteractions(archiveClient);
        verify(auditLogMapper, never()).delete(any());
    }

    @Test
    void shouldNotThrowWhenUploadFails() {
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());
        doThrow(new BizException("whale-file unavailable")).when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver = new AuditLogArchiver(auditLogMapper, archiveClient, exportProperties);

        assertDoesNotThrow(() -> archiver.archive(RETENTION_DAYS));

        verify(archiveClient).upload(any(), any(), any());
        verify(auditLogMapper, never()).delete(any());
    }

    private static AuditLogRecord sampleRecord() {
        AuditLogRecord record = new AuditLogRecord();
        record.setId(1L);
        record.setTraceId("trace-0001");
        record.setUsername("alice");
        record.setModule("whale-auth");
        record.setOperation("创建用户");
        record.setOperateType("CREATE");
        record.setBizStatus(1);
        record.setCreateTime(LocalDateTime.now().minusDays(90));
        return record;
    }
}

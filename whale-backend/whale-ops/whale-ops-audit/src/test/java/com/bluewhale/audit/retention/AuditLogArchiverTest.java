package com.bluewhale.audit.retention;

import com.bluewhale.audit.config.AuditExportProperties;
import com.bluewhale.audit.retention.mapper.AuditArchiveWatermarkMapper;
import com.bluewhale.audit.retention.po.AuditArchiveWatermarkPo;
import com.bluewhale.audit.support.AuditArchiveClient;
import com.bluewhale.audit.support.AuditArchiveDigest;
import com.bluewhale.audit.support.AuditMetrics;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.core.exception.BizException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@code audit_log} 归档任务单测（AUTH-09）：过期记录导出上传、无记录跳过、
 * 上传失败受控、<b>任何路径下均不调用 {@code audit_log} 删除 Mapper 方法</b>、
 * 上传成功推进归档水位且失败不推进。
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
    private AuditArchiveWatermarkMapper watermarkMapper;

    @Mock
    private AuditArchiveClient archiveClient;

    private final AuditExportProperties exportProperties = new AuditExportProperties();

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final AuditMetrics auditMetrics = new AuditMetrics(meterRegistry);

    @Test
    void shouldUploadExpiredRecordsAndNeverDeleteAuditLog() {
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());
        Map<String, Object> uploaded = new HashMap<>();
        uploaded.put("objectKey", "bluewhale-audit-archive/2026/auditLog-archive-20261019.csv");
        doReturn(uploaded).when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        archiver.archive(RETENTION_DAYS);

        // 每批两次上传：归档 CSV + 紧随其后的 .sha256 校验清单
        verify(archiveClient, times(2)).upload(any(), any(), any());
        verify(auditLogMapper, never()).delete(any());
        verify(auditLogMapper, never()).deleteById(any());
        verify(auditLogMapper, never()).deleteByIds(any());
        verify(auditLogMapper, never()).updateById(any(AuditLogRecord.class));
    }

    @Test
    void shouldSkipUploadWhenNoExpiredRecords() {
        doReturn(List.of()).when(auditLogMapper).selectList(any());
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        archiver.archive(RETENTION_DAYS);

        verifyNoInteractions(archiveClient);
        verify(auditLogMapper, never()).delete(any());
        verify(watermarkMapper, never()).insert(any(AuditArchiveWatermarkPo.class));
        verify(watermarkMapper, never()).updateById(any(AuditArchiveWatermarkPo.class));
    }

    @Test
    void shouldNotThrowWhenUploadFails() {
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());
        doThrow(new BizException("whale-file unavailable")).when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        assertDoesNotThrow(() -> archiver.archive(RETENTION_DAYS));

        verify(archiveClient).upload(any(), any(), any());
        verify(auditLogMapper, never()).delete(any());
        verify(auditLogMapper, never()).deleteById(any());
        verify(auditLogMapper, never()).deleteByIds(any());
        verify(auditLogMapper, never()).updateById(any(AuditLogRecord.class));
        verify(watermarkMapper, never()).insert(any(AuditArchiveWatermarkPo.class));
        verify(watermarkMapper, never()).updateById(any(AuditArchiveWatermarkPo.class));
    }

    @Test
    void shouldInsertWatermarkWhenArchiveSucceedsFirstTime() {
        AuditLogRecord record = sampleRecord();
        doReturn(List.of(record)).when(auditLogMapper).selectList(any());
        Map<String, Object> uploaded = new HashMap<>();
        uploaded.put("objectKey", "bluewhale/auditLog-archive.csv");
        doReturn(uploaded).when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        archiver.archive(RETENTION_DAYS);

        ArgumentCaptor<AuditArchiveWatermarkPo> captor = ArgumentCaptor.forClass(AuditArchiveWatermarkPo.class);
        verify(watermarkMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals(record.getCreateTime(), captor.getValue().getLastCreateTime());
        assertEquals(record.getId(), captor.getValue().getLastId());
    }

    @Test
    void shouldArchiveInBatchesAndAdvanceWatermarkPerBatch() {
        exportProperties.setBatchRows(2);
        exportProperties.setMaxRows(10);
        AuditLogRecord first = sampleRecord();
        AuditLogRecord second = sampleRecord();
        second.setId(2L);
        AuditLogRecord third = sampleRecord();
        third.setId(3L);
        doReturn(List.of(first, second), List.of(third)).when(auditLogMapper).selectList(any());
        doReturn(Map.of("objectKey", "bluewhale/auditLog-archive-batch.csv"))
                .when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        archiver.archive(RETENTION_DAYS);

        verify(archiveClient, times(4)).upload(any(), any(), any());
        verify(watermarkMapper).insert(any(AuditArchiveWatermarkPo.class));
        verify(watermarkMapper).updateById(any(AuditArchiveWatermarkPo.class));
    }

    @Test
    void shouldUpdateExistingWatermarkAfterArchive() {
        AuditArchiveWatermarkPo existing = new AuditArchiveWatermarkPo();
        existing.setId(1L);
        existing.setLastCreateTime(LocalDateTime.now().minusDays(120));
        existing.setLastId(1L);
        doReturn(existing).when(watermarkMapper).selectById(1L);
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());
        doReturn(Map.of("objectKey", "bluewhale/auditLog-archive.csv"))
                .when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        archiver.archive(RETENTION_DAYS);

        verify(watermarkMapper).updateById(any(AuditArchiveWatermarkPo.class));
        verify(watermarkMapper, never()).insert(any(AuditArchiveWatermarkPo.class));
    }

    @Test
    @DisplayName("归档批次：水位同时落 CSV 的 SHA-256、链值与两份对象键（完整性可自证）")
    void shouldRecordBatchHashAndChainInWatermark() {
        AuditLogRecord record = sampleRecord();
        doReturn(List.of(record)).when(auditLogMapper).selectList(any());
        doReturn(Map.of("objectKey", "bucket/auditLog-archive-a.csv"),
                Map.of("objectKey", "bucket/auditLog-archive-a.csv.sha256"))
                .when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        archiver.archive(RETENTION_DAYS);

        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(archiveClient, times(2)).upload(contentCaptor.capture(), any(), any());
        String expectedSha = AuditArchiveDigest.sha256Hex(contentCaptor.getAllValues().get(0));

        ArgumentCaptor<AuditArchiveWatermarkPo> captor = ArgumentCaptor.forClass(AuditArchiveWatermarkPo.class);
        verify(watermarkMapper).insert(captor.capture());
        AuditArchiveWatermarkPo saved = captor.getValue();
        assertEquals(expectedSha, saved.getLastBatchSha256(), "水位里的 SHA-256 必须等于实际上传的 CSV");
        assertEquals(AuditArchiveDigest.chainSha256(null, expectedSha), saved.getChainSha256(),
                "首批链值以前驱空串参与计算");
        assertEquals(1, saved.getLastBatchRows());
        assertEquals(1L, saved.getArchivedTotal());
        assertEquals("bucket/auditLog-archive-a.csv", saved.getLastObjectKey());
        assertEquals("bucket/auditLog-archive-a.csv.sha256", saved.getLastManifestKey());
    }

    @Test
    @DisplayName("归档清单：文件名由 CSV 派生，内容含哈希/链值/行数；配置密钥后追加 64 位 HMAC")
    void shouldUploadSignedManifestWithHashAndChain() {
        exportProperties.setSignKey("unit-test-sign-key");
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());
        doReturn(Map.of("objectKey", "bucket/auditLog-archive-b.csv"))
                .when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        archiver.archive(RETENTION_DAYS);

        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(archiveClient, times(2)).upload(contentCaptor.capture(), nameCaptor.capture(), any());
        String manifest = new String(contentCaptor.getAllValues().get(1), StandardCharsets.UTF_8);
        assertTrue(nameCaptor.getAllValues().get(1).endsWith(".csv.sha256"),
                "清单名应由 CSV 名派生，实际=" + nameCaptor.getAllValues().get(1));
        assertTrue(manifest.contains("sha256=" + AuditArchiveDigest.sha256Hex(contentCaptor.getAllValues().get(0))),
                "清单必须记录本批 CSV 的 SHA-256，实际=" + manifest);
        assertTrue(manifest.contains("rows=1"), "清单必须记录本批行数，实际=" + manifest);
        assertTrue(manifest.lines().anyMatch(line -> line.startsWith("hmac=") && line.length() == "hmac=".length() + 64),
                "配置密钥后必须带 64 位 HMAC，实际=" + manifest);
    }

    @Test
    @DisplayName("链式归档：第二批的链值必须以上一批链值为前驱，而非重新起链")
    void shouldChainSecondBatchOntoPreviousChain() {
        String previousChain = "a".repeat(64);
        AuditArchiveWatermarkPo existing = new AuditArchiveWatermarkPo();
        existing.setId(1L);
        existing.setLastCreateTime(LocalDateTime.now().minusDays(120));
        existing.setLastId(1L);
        existing.setChainSha256(previousChain);
        existing.setArchivedTotal(5L);
        doReturn(existing).when(watermarkMapper).selectById(1L);
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());
        doReturn(Map.of("objectKey", "bucket/auditLog-archive-c.csv"))
                .when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        archiver.archive(RETENTION_DAYS);

        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(archiveClient, times(2)).upload(contentCaptor.capture(), any(), any());
        String batchSha = AuditArchiveDigest.sha256Hex(contentCaptor.getAllValues().get(0));

        ArgumentCaptor<AuditArchiveWatermarkPo> captor = ArgumentCaptor.forClass(AuditArchiveWatermarkPo.class);
        verify(watermarkMapper).updateById(captor.capture());
        assertEquals(AuditArchiveDigest.chainSha256(previousChain, batchSha), captor.getValue().getChainSha256());
        assertEquals(6L, captor.getValue().getArchivedTotal(), "累计归档行数应在上一批基础上累加");
    }

    @Test
    @DisplayName("强制签名但密钥为空：直接失败且不产生任何批次（不留无签名归档物）")
    void shouldRefuseToArchiveWhenSignKeyRequiredButMissing() {
        exportProperties.setRequireSignKey(true);
        exportProperties.setSignKey("");
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> archiver.archive(RETENTION_DAYS));
        assertTrue(error.getMessage().contains("sign-key"), "异常信息应指出缺哪个配置，实际=" + error.getMessage());
        verifyNoInteractions(archiveClient);
        verifyNoInteractions(watermarkMapper);
        assertEquals(0.0, meterRegistry.get("audit.archive.signed").gauge().value(),
                "即使拒绝归档，也要把「未签名」这个事实暴露成指标");
    }

    @Test
    @DisplayName("未强制签名但密钥为空：照常归档，指标 audit.archive.signed=0 如实暴露")
    void shouldExposeUnsignedMetricWhenSignKeyMissing() {
        exportProperties.setRequireSignKey(false);
        exportProperties.setSignKey("");
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());
        doReturn(Map.of("objectKey", "bucket/auditLog-archive-u.csv"))
                .when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        archiver.archive(RETENTION_DAYS);

        verify(archiveClient, times(2)).upload(any(), any(), any());
        assertEquals(0.0, meterRegistry.get("audit.archive.signed").gauge().value(),
                "无密钥时签名指标必须为 0，否则监控看不出归档物不可验签");
    }

    @Test
    @DisplayName("配置密钥：签名指标为 1")
    void shouldExposeSignedMetricWhenSignKeyPresent() {
        exportProperties.setSignKey("unit-test-sign-key");
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());
        doReturn(Map.of("objectKey", "bucket/auditLog-archive-s.csv"))
                .when(archiveClient).upload(any(), any(), any());
        AuditLogArchiver archiver =
                new AuditLogArchiver(auditLogMapper, watermarkMapper, archiveClient, exportProperties, auditMetrics);

        archiver.archive(RETENTION_DAYS);

        assertEquals(1.0, meterRegistry.get("audit.archive.signed").gauge().value());
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

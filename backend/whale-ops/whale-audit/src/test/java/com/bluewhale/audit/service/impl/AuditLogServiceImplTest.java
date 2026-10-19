package com.bluewhale.audit.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.audit.config.AuditExportProperties;
import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.audit.support.AuditArchiveClient;
import com.bluewhale.audit.vo.AuditLogVO;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 审计查询服务单测（mock Mapper，覆盖分页映射、单条详情、按 traceId 聚合、分页上限与导出归档）。
 *
 * @author BlueWhale
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class AuditLogServiceImplTest {

    private static final long RECORD_ID = 1251323419503984640L;
    private static final long REQUEST_USER_ID = 1001L;
    private static final String TRACE_ID = "trace-0001";
    private static final String MODULE = "whale-auth";

    private static final String OBJECT_KEY = "bluewhale-audit-archive/2026/auditLog-archive-20261019.csv";

    private final AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
    private final AuditArchiveClient archiveClient = mock(AuditArchiveClient.class);
    private final AuditExportProperties exportProperties = new AuditExportProperties();
    private final AuditLogServiceImpl auditLogService =
            new AuditLogServiceImpl(auditLogMapper, archiveClient, exportProperties);

    @Test
    void shouldReturnPageResultWithMappedRecords() {
        Page<AuditLogRecord> dbPage = new Page<>(1L, 10L);
        dbPage.setRecords(List.of(sampleRecord()));
        dbPage.setTotal(1L);
        doReturn(dbPage).when(auditLogMapper).selectPage(any(Page.class), any());

        AuditLogQueryRequest request = new AuditLogQueryRequest();
        request.setUserId(REQUEST_USER_ID);
        request.setModule(MODULE);
        request.setStartTime(LocalDateTime.now().minusDays(1));
        request.setEndTime(LocalDateTime.now());

        PageResult<AuditLogVO> result = auditLogService.page(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getCurrent()).isEqualTo(1L);
        assertThat(result.getSize()).isEqualTo(10L);
        assertThat(result.getRecords()).hasSize(1);
        AuditLogVO vo = result.getRecords().get(0);
        assertThat(vo.getId()).isEqualTo(RECORD_ID);
        assertThat(vo.getUsername()).isEqualTo("alice");
        assertThat(vo.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(vo.getModule()).isEqualTo(MODULE);
        assertThat(vo.getBizStatus()).isEqualTo(1);
    }

    @Test
    void shouldClampPageSizeToUpperBound() {
        Page<AuditLogRecord> dbPage = new Page<>(1L, 200L);
        dbPage.setRecords(List.of());
        dbPage.setTotal(0L);
        doReturn(dbPage).when(auditLogMapper).selectPage(any(Page.class), any());

        AuditLogQueryRequest request = new AuditLogQueryRequest();
        request.setSize(9999L);

        auditLogService.page(request);

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(auditLogMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
    }

    @Test
    void shouldReturnDetailWhenRecordExists() {
        doReturn(sampleRecord()).when(auditLogMapper).selectById(RECORD_ID);

        AuditLogVO vo = auditLogService.getDetail(RECORD_ID);

        assertThat(vo.getId()).isEqualTo(RECORD_ID);
        assertThat(vo.getRequestUri()).isEqualTo("/user");
        assertThat(vo.getCostMs()).isEqualTo(12L);
    }

    @Test
    void shouldThrowNotFoundWhenRecordMissing() {
        doReturn(null).when(auditLogMapper).selectById(RECORD_ID);

        assertThatThrownBy(() -> auditLogService.getDetail(RECORD_ID))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void shouldListRecordsByTraceId() {
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());

        List<AuditLogVO> records = auditLogService.listByTraceId(TRACE_ID);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getTraceId()).isEqualTo(TRACE_ID);
    }

    @Test
    void shouldReturnEmptyListWhenTraceIdBlank() {
        assertThat(auditLogService.listByTraceId(" ")).isEmpty();
        verify(auditLogMapper, never()).selectList(any());
    }

    @Test
    void shouldUploadCsvAndReturnObjectKeyWhenRecordsExist() {
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());
        Map<String, Object> uploaded = new HashMap<>();
        uploaded.put("objectKey", OBJECT_KEY);
        doReturn(uploaded).when(archiveClient).upload(any(), any(), any());

        Result<Map<String, Object>> result = auditLogService.exportCsv(new AuditLogQueryRequest());

        assertThat(result.getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(result.getData()).containsEntry("objectKey", OBJECT_KEY);
        verify(archiveClient).upload(any(), any(), any());
    }

    @Test
    void shouldSkipUploadAndReturnEmptyDataWhenNoRecords() {
        doReturn(List.of()).when(auditLogMapper).selectList(any());

        Result<Map<String, Object>> result = auditLogService.exportCsv(new AuditLogQueryRequest());

        assertThat(result.getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(result.getData()).isEmpty();
        verify(archiveClient, never()).upload(any(), any(), any());
    }

    @Test
    void shouldFailControlledWithoutThrowingWhenUploadFails() {
        doReturn(List.of(sampleRecord())).when(auditLogMapper).selectList(any());
        doThrow(new BizException("whale-file unavailable")).when(archiveClient).upload(any(), any(), any());

        assertThatCode(() -> {
            Result<Map<String, Object>> result = auditLogService.exportCsv(new AuditLogQueryRequest());
            assertThat(result.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
            assertThat(result.getData()).isNull();
        }).doesNotThrowAnyException();

        verify(archiveClient).upload(any(), any(), any());
    }

    private static AuditLogRecord sampleRecord() {
        AuditLogRecord record = new AuditLogRecord();
        record.setId(RECORD_ID);
        record.setTraceId(TRACE_ID);
        record.setUserId(REQUEST_USER_ID);
        record.setUsername("alice");
        record.setModule(MODULE);
        record.setOperation("创建用户");
        record.setOperateType("CREATE");
        record.setMethod("com.bluewhale.auth.controller.UserController#create");
        record.setRequestUri("/user");
        record.setHttpMethod("POST");
        record.setTargetType("sys_user");
        record.setTargetId("2001");
        record.setResponseCode(0);
        record.setBizStatus(1);
        record.setCostMs(12L);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }
}

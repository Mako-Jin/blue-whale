package com.bluewhale.audit.service.impl;

import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.audit.service.AuditLogService;
import com.bluewhale.audit.support.AuditMetrics;
import com.bluewhale.audit.vo.AuditExportTaskVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 异步导出任务状态流转单测：成功回写行数与上传结果、受控失败保留通用文案、
 * 异常不冒泡、未知任务返回 404 语义。
 *
 * @author BlueWhale
 */
class AuditExportTaskServiceTest {

    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final AuditExportTaskService service = new AuditExportTaskService(
            auditLogService, new SyncTaskExecutor(), new AuditMetrics(new SimpleMeterRegistry()));

    @Test
    void shouldCompleteTaskWithRowsAndUploadResult() {
        Map<String, Object> data = new HashMap<>();
        data.put("objectKey", "bluewhale-audit-archive/auditLog-export.csv");
        data.put("rows", 3);
        when(auditLogService.exportCsv(any())).thenReturn(Result.ok(data));

        AuditExportTaskVO submitted = service.submit(new AuditLogQueryRequest());

        assertThat(submitted.getTaskId()).hasSize(32);
        assertThat(submitted.getStatus()).isEqualTo(AuditExportTaskVO.Status.SUCCESS);
        assertThat(submitted.getRows()).isEqualTo(3);
        assertThat(submitted.getUpload()).containsEntry("objectKey", data.get("objectKey"));
        assertThat(submitted.getFinishedAt()).isNotNull();
        assertThat(service.get(submitted.getTaskId()).getStatus())
                .isEqualTo(AuditExportTaskVO.Status.SUCCESS);
    }

    @Test
    void shouldMarkTaskFailedWhenServiceReturnsFailure() {
        when(auditLogService.exportCsv(any()))
                .thenReturn(Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), "审计日志导出失败，请稍后重试"));

        AuditExportTaskVO submitted = service.submit(new AuditLogQueryRequest());

        assertThat(submitted.getStatus()).isEqualTo(AuditExportTaskVO.Status.FAILED);
        assertThat(submitted.getErrorMessage()).isEqualTo("审计日志导出失败，请稍后重试");
        assertThat(submitted.getRows()).isNull();
    }

    @Test
    void shouldSwallowUnexpectedExceptionIntoFailedStatus() {
        when(auditLogService.exportCsv(any())).thenThrow(new IllegalStateException("boom"));

        AuditExportTaskVO submitted = service.submit(new AuditLogQueryRequest());

        assertThat(submitted.getStatus()).isEqualTo(AuditExportTaskVO.Status.FAILED);
        assertThat(submitted.getErrorMessage()).isEqualTo("审计日志导出失败，请稍后重试");
    }

    @Test
    void shouldRejectUnknownTask() {
        assertThatThrownBy(() -> service.get("not-exists")).isInstanceOf(BizException.class);
    }
}

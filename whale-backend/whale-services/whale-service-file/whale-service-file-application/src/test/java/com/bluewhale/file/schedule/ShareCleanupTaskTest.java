package com.bluewhale.file.schedule;

import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.share.repository.FileShareRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ShareCleanupTask} 到期分享清理、保留期阈值与执行摘要单元测试。
 *
 * @author BlueWhale
 */
class ShareCleanupTaskTest {

    private final FileShareRepository fileShareRepository = mock(FileShareRepository.class);

    private final FileProperties properties = new FileProperties();

    /**
     * 构造使用当前配置的清理任务
     *
     * @return 清理任务
     */
    private ShareCleanupTask task() {
        return new ShareCleanupTask(fileShareRepository, properties);
    }

    @Test
    @DisplayName("purgeExpired：开关关闭时返回跳过摘要且不访问仓储")
    void shouldSkipWhenDisabled() {
        properties.getShare().setPurgeEnabled(false);

        assertEquals("skipped: purge disabled", task().purgeExpired());

        verifyNoInteractions(fileShareRepository);
    }

    @Test
    @DisplayName("purgeExpired：按批循环删除并累计条数")
    void shouldPurgeInBatches() {
        properties.getShare().setPurgeBatchSize(2);
        when(fileShareRepository.purgeExpiredBefore(any(), eq(2))).thenReturn(2, 1);

        assertEquals("purged=3", task().purgeExpired());

        verify(fileShareRepository, times(2)).purgeExpiredBefore(any(), eq(2));
    }

    @Test
    @DisplayName("purgeExpired：保留天数换算为到期阈值")
    void shouldUseRetentionDaysAsThreshold() {
        properties.getShare().setRetentionDays(7);
        when(fileShareRepository.purgeExpiredBefore(any(), anyInt())).thenReturn(0);

        task().purgeExpired();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fileShareRepository).purgeExpiredBefore(captor.capture(), anyInt());
        LocalDateTime expected = LocalDateTime.now().minusDays(7);
        assertFalse(captor.getValue().isBefore(expected.minusHours(1)));
        assertFalse(captor.getValue().isAfter(expected.plusHours(1)));
    }

    @Test
    @DisplayName("purgeExpired：删除异常上抛给调度中心")
    void shouldPropagatePurgeFailure() {
        when(fileShareRepository.purgeExpiredBefore(any(), anyInt()))
                .thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> task().purgeExpired());
    }

    @Test
    @DisplayName("purgeExpired：标注 @AuditLog，不记录参数，执行摘要留痕")
    void shouldAnnotatePurgeWithAuditLog() throws NoSuchMethodException {
        Method method = ShareCleanupTask.class.getMethod("purgeExpired");
        AuditLog annotation = method.getAnnotation(AuditLog.class);

        assertNotNull(annotation, "purgeExpired 应标注 @AuditLog");
        assertEquals(OperateType.DELETE, annotation.type());
        assertEquals("file_share", annotation.targetType());
        assertFalse(annotation.recordParams(), "清理任务无请求参数");
        assertTrue(annotation.recordResult(), "执行摘要需留痕");
    }
}

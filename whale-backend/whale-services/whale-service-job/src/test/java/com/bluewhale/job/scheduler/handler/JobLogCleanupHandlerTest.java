package com.bluewhale.job.scheduler.handler;

import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.mapper.JobLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobLogCleanupHandler} 过期执行日志清理单元测试。
 *
 * @author BlueWhale
 */
class JobLogCleanupHandlerTest {

    private final JobLogMapper jobLogMapper = mock(JobLogMapper.class);

    private static JobLogCleanupHandler handler(int retentionDays) {
        JobProperties properties = new JobProperties();
        properties.setLogRetentionDays(retentionDays);
        return new JobLogCleanupHandler(mock(JobLogMapper.class), properties);
    }

    @Test
    @DisplayName("execute：按配置保留天数清理并返回摘要")
    void shouldCleanupByConfiguredRetentionDays() {
        JobProperties properties = new JobProperties();
        properties.setLogRetentionDays(7);
        JobLogCleanupHandler handler = new JobLogCleanupHandler(jobLogMapper, properties);
        when(jobLogMapper.delete(any())).thenReturn(5);

        String result = handler.execute(null);

        verify(jobLogMapper).delete(any());
        assertTrue(result.contains("7"));
        assertTrue(result.contains("5"));
    }

    @Test
    @DisplayName("execute：未配置时默认保留 30 天")
    void shouldUseDefaultRetentionWhenNotConfigured() {
        JobProperties properties = new JobProperties();
        JobLogCleanupHandler handler = new JobLogCleanupHandler(jobLogMapper, properties);
        when(jobLogMapper.delete(any())).thenReturn(0);

        String result = handler.execute("ignored");

        assertTrue(result.contains("30"));
    }

    @Test
    @DisplayName("execute：无过期数据时摘要显示清理 0 条")
    void shouldReportZeroWhenNothingToClean() {
        JobProperties properties = new JobProperties();
        properties.setLogRetentionDays(1);
        JobLogCleanupHandler handler = new JobLogCleanupHandler(jobLogMapper, properties);
        when(jobLogMapper.delete(any())).thenReturn(0);

        String result = handler.execute(null);

        assertTrue(result.contains("0 条"));
    }

    @Test
    @DisplayName("不同保留天数产生不同的清理时间阈值")
    void shouldUseDifferentThresholdsForDifferentRetentionDays() {
        JobLogCleanupHandler shortRetention = handler(1);
        JobLogCleanupHandler longRetention = handler(365);

        assertTrue(shortRetention.execute(null).contains("1 天"));
        assertTrue(longRetention.execute(null).contains("365 天"));
    }
}

package com.bluewhale.job.service.impl;

import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.mapper.JobExecutorMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobExecutorServiceImpl} 过期行清理测试。
 *
 * <p>清理阈值 = 心跳超时 × 倍数，倍数非法时至少按 1 倍处理；
 * 清理只做物理删除，不参与在线判定。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class JobExecutorServiceImplTest {

    @Mock
    private JobExecutorMapper jobExecutorMapper;

    @Test
    @DisplayName("按心跳超时的倍数计算清理阈值并透传给 Mapper")
    void shouldPurgeWithMultipliedThreshold() {
        JobProperties properties = new JobProperties();
        properties.getExecutor().setHeartbeatTimeoutSeconds(90);
        properties.getExecutor().setStaleMultiplier(10);
        JobExecutorServiceImpl service = new JobExecutorServiceImpl(jobExecutorMapper, properties);
        when(jobExecutorMapper.deleteStale(any())).thenReturn(3);

        int rows = service.purgeStale();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jobExecutorMapper).deleteStale(threshold.capture());
        assertEquals(3, rows);
        LocalDateTime expected = LocalDateTime.now().minusSeconds(900);
        assertTrue(Math.abs(Duration.between(threshold.getValue(), expected).getSeconds()) <= 2,
                "阈值应约为 900 秒前：" + threshold.getValue());
    }

    @Test
    @DisplayName("倍数配置为 0 或负数时按 1 倍处理，不会退化成「删除全部」")
    void shouldClampMultiplierToAtLeastOne() {
        JobProperties properties = new JobProperties();
        properties.getExecutor().setHeartbeatTimeoutSeconds(90);
        properties.getExecutor().setStaleMultiplier(0);
        JobExecutorServiceImpl service = new JobExecutorServiceImpl(jobExecutorMapper, properties);
        when(jobExecutorMapper.deleteStale(any())).thenReturn(0);

        service.purgeStale();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jobExecutorMapper).deleteStale(threshold.capture());
        LocalDateTime expected = LocalDateTime.now().minusSeconds(90);
        assertTrue(Math.abs(Duration.between(threshold.getValue(), expected).getSeconds()) <= 2,
                "阈值应约为 90 秒前：" + threshold.getValue());
    }
}

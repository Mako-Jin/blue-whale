package com.bluewhale.file.schedule;

import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.lock.ScheduledTaskLock;
import com.bluewhale.file.service.FileInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link VersionCleanupTask} 存量版本定时清理单元测试。
 *
 * @author BlueWhale
 */
class VersionCleanupTaskTest {

    private final FileInfoService fileInfoService = mock(FileInfoService.class);

    private final ScheduledTaskLock scheduledTaskLock = mock(ScheduledTaskLock.class);

    private final ScheduledTaskLock.Handle handle = mock(ScheduledTaskLock.Handle.class);

    private FileProperties properties;

    private VersionCleanupTask task;

    /**
     * 每个用例使用独立配置，默认抢锁成功
     */
    @BeforeEach
    void setUp() {
        properties = new FileProperties();
        when(scheduledTaskLock.tryAcquire(anyString(), any(Duration.class))).thenReturn(Optional.of(handle));
        task = new VersionCleanupTask(fileInfoService, properties, scheduledTaskLock);
    }

    @Test
    @DisplayName("cleanup：开关关闭时不触发清理")
    void shouldSkipWhenDisabled() {
        task.cleanup();

        verifyNoInteractions(fileInfoService, scheduledTaskLock);
    }

    @Test
    @DisplayName("cleanup：保留上限非正时不触发清理")
    void shouldSkipWhenUnlimited() {
        properties.getVersion().setCleanupEnabled(true);
        properties.getVersion().setMaxVersions(0);

        task.cleanup();

        verify(fileInfoService, never()).pruneExcessVersions();
        verifyNoInteractions(scheduledTaskLock);
    }

    @Test
    @DisplayName("cleanup：开启且有限额时调用清理并释放锁")
    void shouldPruneWhenEnabled() {
        properties.getVersion().setCleanupEnabled(true);
        properties.getVersion().setMaxVersions(20);
        when(fileInfoService.pruneExcessVersions()).thenReturn(3);

        task.cleanup();

        verify(fileInfoService).pruneExcessVersions();
        verify(handle).close();
    }

    @Test
    @DisplayName("cleanup：其他实例持锁时跳过本轮")
    void shouldSkipWhenLockBusy() {
        properties.getVersion().setCleanupEnabled(true);
        properties.getVersion().setMaxVersions(20);
        when(scheduledTaskLock.tryAcquire(anyString(), any(Duration.class))).thenReturn(Optional.empty());

        task.cleanup();

        verify(fileInfoService, never()).pruneExcessVersions();
    }

    @Test
    @DisplayName("cleanup：清理异常时仍释放锁")
    void shouldReleaseLockWhenPruneFails() {
        properties.getVersion().setCleanupEnabled(true);
        properties.getVersion().setMaxVersions(20);
        when(fileInfoService.pruneExcessVersions()).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, task::cleanup);

        verify(handle).close();
    }
}

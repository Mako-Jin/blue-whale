package com.bluewhale.file.schedule;

import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.service.FileInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link VersionCleanupTask} 存量版本定时清理与执行摘要单元测试。
 *
 * @author BlueWhale
 */
class VersionCleanupTaskTest {

    private final FileInfoService fileInfoService = mock(FileInfoService.class);

    private FileProperties properties;

    private VersionCleanupTask task;

    /**
     * 每个用例使用独立配置
     */
    @BeforeEach
    void setUp() {
        properties = new FileProperties();
        task = new VersionCleanupTask(fileInfoService, properties);
    }

    @Test
    @DisplayName("cleanup：开关关闭时返回跳过摘要且不触发清理")
    void shouldSkipWhenDisabled() {
        assertEquals("skipped: cleanup disabled", task.cleanup());

        verifyNoInteractions(fileInfoService);
    }

    @Test
    @DisplayName("cleanup：保留上限非正时返回跳过摘要")
    void shouldSkipWhenUnlimited() {
        properties.getVersion().setCleanupEnabled(true);
        properties.getVersion().setMaxVersions(0);

        assertEquals("skipped: max-versions<=0", task.cleanup());

        verify(fileInfoService, never()).pruneExcessVersions();
    }

    @Test
    @DisplayName("cleanup：开启且有限额时调用清理并返回条数")
    void shouldPruneWhenEnabled() {
        properties.getVersion().setCleanupEnabled(true);
        properties.getVersion().setMaxVersions(20);
        when(fileInfoService.pruneExcessVersions()).thenReturn(3);

        assertEquals("pruned=3", task.cleanup());

        verify(fileInfoService).pruneExcessVersions();
    }

    @Test
    @DisplayName("cleanup：清理异常时上抛给调度中心")
    void shouldPropagatePruneFailure() {
        properties.getVersion().setCleanupEnabled(true);
        properties.getVersion().setMaxVersions(20);
        when(fileInfoService.pruneExcessVersions()).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, task::cleanup);
    }
}

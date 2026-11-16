package com.bluewhale.file.schedule;

import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.service.FileInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("cleanup：开关关闭时不触发清理")
    void shouldSkipWhenDisabled() {
        task.cleanup();

        verifyNoInteractions(fileInfoService);
    }

    @Test
    @DisplayName("cleanup：保留上限非正时不触发清理")
    void shouldSkipWhenUnlimited() {
        properties.getVersion().setCleanupEnabled(true);
        properties.getVersion().setMaxVersions(0);

        task.cleanup();

        verify(fileInfoService, never()).pruneExcessVersions();
    }

    @Test
    @DisplayName("cleanup：开启且有限额时调用清理")
    void shouldPruneWhenEnabled() {
        properties.getVersion().setCleanupEnabled(true);
        properties.getVersion().setMaxVersions(20);
        when(fileInfoService.pruneExcessVersions()).thenReturn(3);

        task.cleanup();

        verify(fileInfoService).pruneExcessVersions();
    }
}

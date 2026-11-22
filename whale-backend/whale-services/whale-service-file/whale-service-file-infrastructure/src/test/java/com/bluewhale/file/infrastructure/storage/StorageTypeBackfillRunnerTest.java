package com.bluewhale.file.infrastructure.storage;

import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.mapper.FileInfoMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StorageTypeBackfillRunner} 启动期存储类型回填单元测试：覆盖正常回填、
 * 无历史行与回填失败降级。
 *
 * @author BlueWhale
 */
class StorageTypeBackfillRunnerTest {

    private final FileInfoMapper fileInfoMapper = mock(FileInfoMapper.class);

    private final StoragePluginCatalog catalog = mock(StoragePluginCatalog.class);

    private final StorageTypeBackfillRunner runner =
            new StorageTypeBackfillRunner(fileInfoMapper, catalog);

    private final ApplicationArguments args = mock(ApplicationArguments.class);

    @Test
    @DisplayName("run：读取活动插件类型并回填历史行")
    void shouldBackfillWithActiveType() {
        when(catalog.activeType()).thenReturn("minio");
        when(fileInfoMapper.backfillStorageType("minio")).thenReturn(7);

        runner.run(args);

        verify(catalog).activeType();
        verify(fileInfoMapper).backfillStorageType("minio");
    }

    @Test
    @DisplayName("run：无历史行可回填时正常结束")
    void shouldCompleteWhenNoRowsBackfilled() {
        when(catalog.activeType()).thenReturn("local");
        when(fileInfoMapper.backfillStorageType("local")).thenReturn(0);

        runner.run(args);

        verify(fileInfoMapper).backfillStorageType("local");
    }

    @Test
    @DisplayName("run：回填异常仅告警，不影响服务启动")
    void shouldSwallowBackfillFailure() {
        when(catalog.activeType()).thenReturn("local");
        when(fileInfoMapper.backfillStorageType("local"))
                .thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> runner.run(args));
    }
}

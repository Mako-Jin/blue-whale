package com.bluewhale.file.schedule;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.model.FileRecycleItem;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.metrics.FileMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RecyclePurgeTask} 清理流程与执行摘要单元测试。
 *
 * @author BlueWhale
 */
class RecyclePurgeTaskTest {

    private final FileAssetRepository fileAssetRepository = mock(FileAssetRepository.class);

    private final FileStorage fileStorage = mock(FileStorage.class);

    private final FileMetrics fileMetrics = mock(FileMetrics.class);

    private final FileProperties fileProperties = new FileProperties();

    /**
     * 构造清理任务
     *
     * @param enabled 是否启用
     * @param retentionDays 保留天数
     * @return 清理任务
     */
    private RecyclePurgeTask task(boolean enabled, long retentionDays) {
        fileProperties.getRecycle().setPurgeEnabled(enabled);
        fileProperties.getRecycle().setRetentionDays(retentionDays);
        fileProperties.getRecycle().setPurgeBatchSize(2);
        return new RecyclePurgeTask(fileAssetRepository, fileStorage, fileProperties, fileMetrics);
    }

    /**
     * 构造回收站投影
     *
     * @param id 文件ID
     * @return 回收站投影
     */
    private static FileRecycleItem item(long id) {
        return new FileRecycleItem(id, "stored.pdf", "origin.pdf", "bluewhale", "local", "tenants/1/a.pdf",
                12L, "application/pdf", 1, 9L, LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now().minusDays(31));
    }

    @Test
    @DisplayName("开关关闭或保留天数非正时不查询、不删除，返回跳过摘要")
    void shouldSkipWhenDisabled() {
        assertEquals("skipped: purge disabled", task(false, 30L).purgeExpired());
        assertEquals("skipped: retention-days<=0", task(true, 0L).purgeExpired());

        verify(fileAssetRepository, never()).findExpiredRecycle(any(), anyLong());
    }

    @Test
    @DisplayName("按批清理：先删物理对象再物理删除元数据，摘要含清理条数")
    void shouldPurgeExpiredInBatches() {
        when(fileAssetRepository.findExpiredRecycle(any(), anyLong()))
                .thenReturn(List.of(item(1L), item(2L)))
                .thenReturn(List.of());
        when(fileAssetRepository.purgeById(anyLong())).thenReturn(true);

        assertEquals("purged=2", task(true, 30L).purgeExpired());

        verify(fileStorage, times(2)).delete("bluewhale", "tenants/1/a.pdf", "local");
        verify(fileAssetRepository).purgeById(1L);
        verify(fileAssetRepository).purgeById(2L);
        verify(fileMetrics, times(2)).recordPurge(true);
    }

    @Test
    @DisplayName("物理删除失败：保留元数据记录并计失败，不阻断同批其他记录")
    void shouldKeepRecordWhenStorageDeleteFails() {
        when(fileAssetRepository.findExpiredRecycle(any(), anyLong()))
                .thenReturn(List.of(item(1L), item(2L)))
                .thenReturn(List.of());
        doThrow(new BizException(ErrorCode.SYSTEM_ERROR)).when(fileStorage)
                .delete("bluewhale", "tenants/1/a.pdf", "local");
        when(fileAssetRepository.purgeById(anyLong())).thenReturn(true);

        assertEquals("purged=0", task(true, 30L).purgeExpired());

        verify(fileAssetRepository, never()).purgeById(1L);
        verify(fileAssetRepository, never()).purgeById(2L);
        // 本轮无进展即退出，失败记录只尝试一次，不会在 10 轮内重复扫描
        verify(fileStorage, times(2)).delete("bluewhale", "tenants/1/a.pdf", "local");
        verify(fileMetrics, times(2)).recordPurge(false);
    }
}

package com.bluewhale.file.health;

import com.bluewhale.file.domain.storage.ChunkUploadStore;
import com.bluewhale.file.domain.storage.StorageHealth;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link StorageHealthIndicator} 汇总规则单元测试。
 *
 * @author BlueWhale
 */
class StorageHealthIndicatorTest {

    /**
     * 构造健康指示器桩。
     *
     * @param activeHealth 活动插件健康状态
     * @param chunkWritable 分片暂存目录是否可写
     * @return 健康指示器
     */
    private static StorageHealthIndicator indicator(StorageHealth activeHealth, boolean chunkWritable) {
        StoragePluginCatalog catalog = mock(StoragePluginCatalog.class);
        when(catalog.activeType()).thenReturn("local");
        when(catalog.health()).thenReturn(Map.of("local", activeHealth));
        ChunkUploadStore chunkUploadStore = mock(ChunkUploadStore.class);
        when(chunkUploadStore.writable()).thenReturn(chunkWritable);
        return new StorageHealthIndicator(catalog, chunkUploadStore);
    }

    @Test
    @DisplayName("活动插件可用且暂存目录可写时整体 UP")
    void shouldBeUpWhenStorageAvailable() {
        Health health = indicator(StorageHealth.up("ok"), true).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("local", health.getDetails().get("active"));
        assertEquals("UP", health.getDetails().get("chunkUploadStore"));
    }

    @Test
    @DisplayName("活动插件 DOWN 时整体 DOWN，并保留失败原因")
    void shouldBeDownWhenActivePluginDown() {
        Health health = indicator(StorageHealth.down("桶不存在: bluewhale"), true).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(String.valueOf(health.getDetails().get("plugin.local")).contains("桶不存在"));
    }

    @Test
    @DisplayName("分片暂存目录不可写时整体 DOWN")
    void shouldBeDownWhenChunkStoreNotWritable() {
        Health health = indicator(StorageHealth.up("ok"), false).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("DOWN", health.getDetails().get("chunkUploadStore"));
    }
}

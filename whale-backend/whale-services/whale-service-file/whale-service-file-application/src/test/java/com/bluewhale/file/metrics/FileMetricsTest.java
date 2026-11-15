package com.bluewhale.file.metrics;

import com.bluewhale.file.domain.storage.ChunkUploadStore;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.domain.storage.StoragePluginInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link FileMetrics} 计数、耗时与 Gauge 注册单元测试。
 *
 * @author BlueWhale
 */
class FileMetricsTest {

    @Test
    @DisplayName("上传/下载/恢复按结果与模式累计，活跃会话与活动插件以 Gauge 上报")
    void shouldRecordMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ChunkUploadStore chunkUploadStore = mock(ChunkUploadStore.class);
        when(chunkUploadStore.sessionCount()).thenReturn(3L);
        StoragePluginCatalog catalog = mock(StoragePluginCatalog.class);
        when(catalog.activeType()).thenReturn("local");
        when(catalog.plugins()).thenReturn(List.of(
                new StoragePluginInfo("local", "local-plugin", false),
                new StoragePluginInfo("minio", "minio-plugin", true)));
        FileMetrics metrics = new FileMetrics(registry, chunkUploadStore, catalog);

        metrics.recordUpload("multipart", 10L, 1_000_000L, "success");
        metrics.recordUpload("multipart", 10L, 1_000_000L, "failure");
        metrics.recordDownload(20L, 2_000_000L);
        metrics.recordRestore(true);
        metrics.recordRestore(false);

        assertEquals(1.0, registry.get("file.upload")
                .tag("mode", "multipart").tag("result", "success").counter().count());
        assertEquals(1.0, registry.get("file.upload")
                .tag("mode", "multipart").tag("result", "failure").counter().count());
        assertEquals(10.0, registry.get("file.upload.size")
                .tag("mode", "multipart").summary().totalAmount());
        assertEquals(2L, registry.get("file.upload.duration")
                .tag("mode", "multipart").timer().count());
        assertEquals(1.0, registry.get("file.download").counter().count());
        assertEquals(20.0, registry.get("file.download.size").summary().totalAmount());
        assertEquals(1.0, registry.get("file.recycle.restore").tag("result", "success").counter().count());
        assertEquals(1.0, registry.get("file.recycle.restore").tag("result", "failure").counter().count());
        assertEquals(3.0, registry.get("file.chunk.session.active").gauge().value());
        assertEquals(1.0, registry.get("file.storage.plugin.active").tag("type", "local").gauge().value());
        assertEquals(0.0, registry.get("file.storage.plugin.active").tag("type", "minio").gauge().value());
    }
}

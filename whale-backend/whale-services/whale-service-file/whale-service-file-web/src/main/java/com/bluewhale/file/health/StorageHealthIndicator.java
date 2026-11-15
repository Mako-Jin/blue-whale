package com.bluewhale.file.health;

import com.bluewhale.file.domain.storage.ChunkUploadStore;
import com.bluewhale.file.domain.storage.StorageHealth;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 存储健康指示器：把存储插件与分片暂存介质的可用性汇总到 {@code /actuator/health}。
 *
 * <p>活动插件不可用或分片暂存目录不可写时整体 DOWN；其余插件的健康状态以明细上报，
 * 不参与整体判定（非活动插件故障不影响本实例读写）。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class StorageHealthIndicator implements HealthIndicator {

    private final StoragePluginCatalog storagePluginCatalog;

    private final ChunkUploadStore chunkUploadStore;

    @Override
    public Health health() {
        String activeType = storagePluginCatalog.activeType();
        Map<String, StorageHealth> plugins = storagePluginCatalog.health();
        StorageHealth activeHealth = plugins.get(activeType);
        boolean chunkWritable = chunkUploadStore.writable();
        boolean up = activeHealth != null && activeHealth.up() && chunkWritable;

        Health.Builder builder = up ? Health.up() : Health.down();
        builder.withDetail("active", activeType);
        builder.withDetail("chunkUploadStore", chunkWritable ? "UP" : "DOWN");
        plugins.forEach((type, health) -> builder.withDetail("plugin." + type,
                health.up() ? "UP: " + health.message() : "DOWN: " + health.message()));
        if (activeHealth == null) {
            builder.withDetail("activeHealth", "UNKNOWN");
        }
        return builder.build();
    }
}

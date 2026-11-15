package com.bluewhale.file.infrastructure.storage;

import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.mapper.FileInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 历史文件落盘存储类型回填：为 {@code storage_type IS NULL} 的存量行写入当前活动插件类型。
 *
 * <p>V3 迁移只加列不写死介质类型（local / minio 环境不同）；回填在启动期按
 * {@code whale.file.storage.type} 完成。启动期无租户上下文，租户插件按既有约定跳过租户条件，
 * 可覆盖全部存量行；回填失败仅告警，读取侧仍有「空值回落活动插件」兜底。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageTypeBackfillRunner implements ApplicationRunner {

    private final FileInfoMapper fileInfoMapper;

    private final StoragePluginCatalog storagePluginCatalog;

    @Override
    public void run(ApplicationArguments args) {
        String activeType = storagePluginCatalog.activeType();
        try {
            int rows = fileInfoMapper.backfillStorageType(activeType);
            if (rows > 0) {
                log.info("历史文件存储类型回填完成: storageType={}, rows={}", activeType, rows);
            }
        } catch (RuntimeException e) {
            log.warn("历史文件存储类型回填失败，读取时将回落活动插件: storageType={}", activeType, e);
        }
    }
}

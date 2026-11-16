package com.bluewhale.file.schedule;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.model.FileObjectRef;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.StorageObject;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.domain.storage.StoragePluginInfo;
import com.bluewhale.file.metrics.FileMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 孤儿对象对账任务：比较存储介质中的对象与元数据引用。
 *
 * <p>差异口径：</p>
 * <ul>
 *   <li>孤儿对象：存储有、元数据无（逻辑删除行仍视为引用，其对象由回收站清理任务负责）；</li>
 *   <li>悬空元数据：元数据有、存储无（可能因迁移中断、备份恢复或最终一致性暂时缺失）。</li>
 * </ul>
 *
 * <p>默认只统计与打样日志，不删除任何内容；仅当显式开启 {@code delete-orphan-objects}
 * 才删除孤儿对象，悬空元数据永不自动删除。任一介质列举失败时整轮放弃并计失败，
 * 避免把「无法列举」误判为“该介质没有对象”从而误删。</p>
 *
 * <p>由调度中心（whale-job）托管，任务编码 {@code file.orphan-reconcile}；
 * 介质列举失败时上抛异常，由调度中心记录失败并按重试策略告警。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanReconcileTask {

    /** 对账键分隔符：存储类型 + 桶 + 对象键 */
    private static final String KEY_SEPARATOR = "|";

    private final FileAssetRepository fileAssetRepository;

    private final FileStorage fileStorage;

    private final StoragePluginCatalog storagePluginCatalog;

    private final FileProperties fileProperties;

    private final FileMetrics fileMetrics;

    /**
     * 按配置周期执行对账；默认关闭，避免未经评估的全量扫描。
     *
     * @return 执行摘要（差异计数或跳过原因）
     */
    @ScheduledJob(code = "file.orphan-reconcile",
            cron = "${whale.file.orphan.cron:0 0 4 * * ?}",
            timeoutSeconds = 7200)
    public String reconcile() {
        FileProperties.Orphan config = fileProperties.getOrphan();
        if (!config.isEnabled()) {
            return "skipped: orphan reconcile disabled";
        }
        String activeType = storagePluginCatalog.activeType();
        Set<String> dbKeys = new HashSet<>();
        for (FileObjectRef ref : fileAssetRepository.findAllObjectRefs()) {
            dbKeys.add(keyOf(normalizeType(ref.storageType(), activeType), ref.bucket(), ref.objectKey()));
        }

        List<OrphanCandidate> orphans = new ArrayList<>();
        Set<String> storageKeys = new HashSet<>();
        for (StoragePluginInfo plugin : storagePluginCatalog.plugins()) {
            List<StorageObject> objects;
            try {
                objects = fileStorage.listObjects(plugin.type());
            } catch (RuntimeException e) {
                fileMetrics.recordOrphanReconcile(0, 0, false);
                log.warn("存储对象列举失败，本轮对账放弃: storageType={}", plugin.type(), e);
                throw new IllegalStateException("存储对象列举失败: storageType=" + plugin.type(), e);
            }
            for (StorageObject object : objects) {
                String key = keyOf(plugin.type(), object.bucket(), object.objectKey());
                storageKeys.add(key);
                if (!dbKeys.contains(key)) {
                    orphans.add(new OrphanCandidate(plugin.type(), object));
                }
            }
        }

        Set<String> dangling = new HashSet<>(dbKeys);
        dangling.removeAll(storageKeys);

        int deleted = deleteOrphans(orphans);
        fileMetrics.recordOrphanReconcile(orphans.size(), dangling.size(), true);
        log.info("孤儿对象对账完成: orphanObjects={}, danglingMetadata={}, deleted={}",
                orphans.size(), dangling.size(), deleted);
        logSamples("孤儿对象", orphans.stream().map(OrphanCandidate::describe).toList());
        logSamples("悬空元数据", dangling);
        return "orphanObjects=" + orphans.size() + ", danglingMetadata=" + dangling.size()
                + ", deleted=" + deleted;
    }

    /**
     * 按配置删除孤儿对象
     *
     * @param orphans 孤儿对象候选
     * @return 成功删除数量
     */
    private int deleteOrphans(List<OrphanCandidate> orphans) {
        if (!fileProperties.getOrphan().isDeleteOrphanObjects()) {
            return 0;
        }
        int deleted = 0;
        for (OrphanCandidate orphan : orphans) {
            try {
                fileStorage.delete(orphan.object().bucket(), orphan.object().objectKey(), orphan.storageType());
                deleted++;
            } catch (RuntimeException e) {
                log.warn("孤儿对象删除失败，跳过: {}", orphan.describe(), e);
            }
        }
        return deleted;
    }

    /**
     * 打印差异样例，便于人工复核
     *
     * @param label 差异类型
     * @param samples 差异项
     */
    private void logSamples(String label, Collection<String> samples) {
        int limit = Math.max(fileProperties.getOrphan().getReportSamples(), 0);
        if (limit == 0) {
            return;
        }
        samples.stream().limit(limit).forEach(sample -> log.info("{} 样例: {}", label, sample));
    }

    /**
     * 归一化存储类型：历史空值按当前活动插件归属
     *
     * @param storageType 元数据中的存储类型
     * @param activeType 当前活动插件类型
     * @return 归一化后的类型
     */
    private static String normalizeType(String storageType, String activeType) {
        return StringUtils.hasText(storageType) ? storageType.trim() : activeType;
    }

    /**
     * 构造对账键
     *
     * @param storageType 存储类型
     * @param bucket 桶名称
     * @param objectKey 对象键
     * @return 对账键
     */
    private static String keyOf(String storageType, String bucket, String objectKey) {
        return storageType + KEY_SEPARATOR + bucket + KEY_SEPARATOR + objectKey;
    }

    /**
     * 孤儿对象候选：保留存储类型用于按介质路由删除
     *
     * @param storageType 落盘介质
     * @param object 存储对象
     */
    private record OrphanCandidate(String storageType, StorageObject object) {

        /**
         * 输出可读的差异描述
         *
         * @return 差异描述
         */
        private String describe() {
            return storageType + "://" + object.bucket() + "/" + object.objectKey() + " (" + object.size() + "B)";
        }
    }
}

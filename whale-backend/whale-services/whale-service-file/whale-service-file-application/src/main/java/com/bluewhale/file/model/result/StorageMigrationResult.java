package com.bluewhale.file.model.result;

import java.util.List;

/**
 * 存储迁移结果汇总。
 *
 * @param sourceStorageType 源落盘存储类型
 * @param targetStorageType 目标（活动）存储类型
 * @param scanned 本批扫描条数
 * @param migrated 成功迁移条数；dry-run 时为待迁移条数
 * @param skipped 因桶过滤或类型不符跳过的条数
 * @param failed 失败条数
 * @param dryRun 是否为演练
 * @param failures 失败样例（最多若干条，便于排查）
 * @author BlueWhale
 */
public record StorageMigrationResult(String sourceStorageType, String targetStorageType, int scanned,
                                     int migrated, int skipped, int failed, boolean dryRun,
                                     List<String> failures) {
}

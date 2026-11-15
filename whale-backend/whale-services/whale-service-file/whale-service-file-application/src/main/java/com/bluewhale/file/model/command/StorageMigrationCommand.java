package com.bluewhale.file.model.command;

/**
 * 存储迁移命令：把指定源介质的文件复制到当前活动插件。
 *
 * @param sourceStorageType 源落盘存储类型
 * @param bucket 桶过滤条件；为 {@code null} 时不限桶
 * @param limit 单批最大处理条数
 * @param dryRun 是否只统计不执行
 * @param deleteSource 迁移成功后是否删除源对象
 * @param confirm 非 dry-run 时必须为 {@code true}
 * @author BlueWhale
 */
public record StorageMigrationCommand(String sourceStorageType, String bucket, long limit,
                                      boolean dryRun, boolean deleteSource, boolean confirm) {
}

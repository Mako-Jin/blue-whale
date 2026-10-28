package com.bluewhale.file.domain.file.repository;

import java.time.LocalDateTime;

/**
 * 文件资产分页查询条件（领域层查询模型，与接口层 DTO 解耦）。
 *
 * @param originalName 原始文件名（模糊匹配），可为 {@code null}
 * @param bucket 对象存储桶名称（精确匹配），可为 {@code null}
 * @param createTimeStart 创建时间下界（含），可为 {@code null}
 * @param createTimeEnd 创建时间上界（含），可为 {@code null}
 * @param current 页码（从 1 开始）
 * @param size 每页条数
 * @author BlueWhale
 */
public record FileAssetQuery(String originalName, String bucket, LocalDateTime createTimeStart,
                             LocalDateTime createTimeEnd, long current, long size) {
}

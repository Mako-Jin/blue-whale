package com.bluewhale.file.domain.model;

import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;

import java.util.List;

/**
 * 文件资产分页结果（领域层分页模型，避免领域端口依赖框架分页类型）。
 *
 * @param records 当前页文件资产
 * @param total 总条数
 * @param current 当前页码
 * @param size 每页条数
 * @author BlueWhale
 */
public record FileAssetPage(List<FileAssetAggregate> records, long total, long current, long size) {
}

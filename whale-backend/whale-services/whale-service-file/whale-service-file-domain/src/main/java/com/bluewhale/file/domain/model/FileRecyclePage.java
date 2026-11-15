package com.bluewhale.file.domain.model;

import java.util.List;

/**
 * 回收站分页结果。
 *
 * @param records 当前页记录
 * @param total 总条数
 * @param current 当前页码
 * @param size 每页条数
 * @author BlueWhale
 */
public record FileRecyclePage(List<FileRecycleItem> records, long total, long current, long size) {
}

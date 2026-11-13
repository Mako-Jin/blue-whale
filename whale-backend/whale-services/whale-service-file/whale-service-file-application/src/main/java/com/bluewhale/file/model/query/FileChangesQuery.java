package com.bluewhale.file.model.query;

import java.time.LocalDateTime;

/**
 * 文件变更流查询（内网接口专用）。
 *
 * @param since 游标下界（不含），可为 {@code null} 表示不限
 * @param current 页码（小于 1 时按 1 处理）
 * @param size 每页条数（1..200，越界自动收敛）
 * @author BlueWhale
 */
public record FileChangesQuery(LocalDateTime since, long current, long size) {
}

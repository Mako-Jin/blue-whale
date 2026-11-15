package com.bluewhale.file.domain.share.model;

import java.util.List;

/**
 * 分享链接分页结果。
 *
 * @param records 当前页记录
 * @param total 总条数
 * @param current 当前页码
 * @param size 每页条数
 * @author BlueWhale
 */
public record FileSharePage(List<FileShareItem> records, long total, long current, long size) {
}

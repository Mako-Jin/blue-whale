package com.bluewhale.file.model.result;

/**
 * 打包下载的单个条目（已通过可见性校验并完成条目名去重）。
 *
 * @param id 文件ID
 * @param entryName ZIP 内的条目名（已剥离目录、去重）
 * @param fileSize 文件字节数
 * @author BlueWhale
 */
public record FileArchiveEntryResult(Long id, String entryName, long fileSize) {
}

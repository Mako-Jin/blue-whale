package com.bluewhale.file.model.result;

/**
 * 秒传结果：命中时携带新登记的文件元数据。
 *
 * @param hit 是否命中已有物理对象
 * @param file 命中时新登记的文件元数据；未命中时为 {@code null}
 * @author BlueWhale
 */
public record FileRapidUploadResult(boolean hit, FileUploadResult file) {
}

package com.bluewhale.file.model.result;

/**
 * 批量删除的单项结果。
 *
 * @param id 文件ID
 * @param success 是否删除成功
 * @param message 失败原因；成功时为 {@code null}
 * @author BlueWhale
 */
public record FileBatchDeleteItemResult(Long id, boolean success, String message) {
}

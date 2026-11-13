package com.bluewhale.auth.interfaces.dto.response;

import java.util.List;

/**
 * 导入预检响应（权限中心扩展 M4）。
 *
 * @param batchId    批次 ID（存在行级错误时为 {@code null}，须修正后重新上传）
 * @param totalRows  文件数据行数（不含表头与全空行）
 * @param validCount 通过校验的行数
 * @param errors     行级错误清单（全量返回，不截断）
 * @author BlueWhale
 */
public record ImportPreviewResponse(String batchId, int totalRows, int validCount, List<RowError> errors) {

    /**
     * 行级错误项：定位到「行 + 列」
     *
     * @param rowNo   Excel 行号（1 起，表头为第 1 行）
     * @param column  列名（与模板表头一致）
     * @param message 错误说明
     * @author BlueWhale
     */
    public record RowError(int rowNo, String column, String message) {
    }
}

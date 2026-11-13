package com.bluewhale.auth.application.model.importexport;

import java.util.List;

/**
 * 导入预检结果（权限中心扩展 M4）。
 *
 * <p>预检不落库：仅当全部行通过校验时才把通过行暂存批次存储并返回 {@code batchId}；
 * 存在任意行级错误时 {@code batchId} 为 {@code null}，调用方须修正后重新上传，
 * 错误清单不做截断（验收要求 1 万行全量报错定位到行/列）。</p>
 *
 * @param batchId    批次 ID（存在错误时为 {@code null}，批次 30 分钟有效）
 * @param totalRows  文件数据行数（不含表头与全空行）
 * @param validCount 通过校验的行数
 * @param errors     行级错误清单（通过时为空列表）
 * @author BlueWhale
 */
public record ImportPreviewResult(String batchId, int totalRows, int validCount, List<ImportRowError> errors) {
}

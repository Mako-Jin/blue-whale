package com.bluewhale.auth.application.model.importexport;

import java.util.Map;

/**
 * 用户导出结果（权限中心扩展 M4）。
 *
 * <p>≤5 万行直接返回 xlsx 字节流同步下载；超过 5 万行同步生成后上传 whale-file
 * （实施偏差：当前无异步任务基建，不走异步，上传成功后返回 {@code objectKey}）。</p>
 *
 * @param content    xlsx 字节内容（直传分支；上传分支为 {@code null}）
 * @param remoteFile whale-file 上传结果（上传分支；含 {@code objectKey}/{@code bucket} 等，直传分支为 {@code null}）
 * @param totalRows  导出总行数
 * @author BlueWhale
 */
public record ExportResult(byte[] content, Map<String, Object> remoteFile, long totalRows) {
}

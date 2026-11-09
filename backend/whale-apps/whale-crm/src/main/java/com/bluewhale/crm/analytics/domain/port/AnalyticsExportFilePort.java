package com.bluewhale.crm.analytics.domain.port;

/**
 * 分析导出文件存储端口。
 *
 * <p>导出 CSV 归 whale-file 统一存储：本端口只提交「文件名 + 字节内容」，
 * 由基础设施适配器上传并返回文件 ID，领域层不感知具体存储实现。</p>
 *
 * @author BlueWhale
 */
public interface AnalyticsExportFilePort {

    /**
     * 上传导出文件。
     *
     * @param fileName   文件名（含扩展名）
     * @param content    UTF-8 字节内容
     * @param uploaderId 上传人（决定归属，决定后续谁可下载）
     * @param tenantId   租户 ID（决定文件落盘与可见性归属）
     * @return whale-file 文件 ID
     */
    Long upload(String fileName, byte[] content, Long uploaderId, Long tenantId);
}

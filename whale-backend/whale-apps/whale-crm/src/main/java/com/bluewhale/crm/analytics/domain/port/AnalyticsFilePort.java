package com.bluewhale.crm.analytics.domain.port;

/**
 * 分析导出文件端口：把导出产物存入 whale-file 并支持代理回读。
 *
 * <p>领域/应用层只依赖本端口，具体 HTTP 交互由基础设施层实现。</p>
 *
 * @author BlueWhale
 */
public interface AnalyticsFilePort {

    /**
     * 上传导出文件。
     *
     * @param content 文件内容
     * @param fileName 文件名（含扩展名）
     * @param userId 归属用户ID（回填上传人，{@code null} 则租户内可见）
     * @return whale-file 文件ID
     */
    Long upload(byte[] content, String fileName, Long userId);

    /**
     * 读取文件内容（内网代理下载）。
     *
     * @param fileId 文件ID
     * @return 文件字节；不可用时返回 {@code null}
     */
    byte[] download(Long fileId);
}

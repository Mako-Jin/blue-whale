package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分析导出任务。
 *
 * <p>V13 起新导出接入 whale-file，产物以 {@code fileId / fileName / fileSize} 引用，
 * 下载经 CRM 代理流式转发；{@code content} 仅保留 V13 前的历史导出，读取时向后兼容。</p>
 *
 * @author BlueWhale
 */
@Data
public class AnalyticsExport {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 导出单号 */
    private String exportNo;

    /** 导出类型 */
    private ExportType exportType;

    /** 筛选条件 JSON */
    private String filtersJson;

    /** 状态 */
    private ExportStatus status;

    /** 数据行数 */
    private Integer rowCount;

    /** CSV 内容（V13 前历史导出；新导出不再写入） */
    private String content;

    /** 导出文件ID（whale-file） */
    private Long fileId;

    /** 导出文件名（含扩展名） */
    private String fileName;

    /** 导出文件大小（字节） */
    private Long fileSize;

    /** 失败原因 */
    private String error;

    /** 发起人 */
    private Long requestedBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 标记导出完成（历史口径：内容落库）。
     *
     * @param csv 已脱敏的 CSV 内容
     * @param rows 数据行数
     */
    public void complete(String csv, int rows) {
        this.status = ExportStatus.COMPLETED;
        this.content = csv;
        this.rowCount = rows;
        this.error = null;
    }

    /**
     * 标记导出完成（V13 口径：内容存 whale-file，仅引用）。
     *
     * @param fileId whale-file 文件ID
     * @param fileName 导出文件名
     * @param fileSize 文件大小（字节）
     * @param rows 数据行数
     */
    public void completeToFile(Long fileId, String fileName, Long fileSize, int rows) {
        this.status = ExportStatus.COMPLETED;
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.rowCount = rows;
        this.error = null;
    }

    /**
     * 标记导出失败。
     *
     * @param message 失败原因
     */
    public void fail(String message) {
        this.status = ExportStatus.FAILED;
        this.error = message;
    }
}

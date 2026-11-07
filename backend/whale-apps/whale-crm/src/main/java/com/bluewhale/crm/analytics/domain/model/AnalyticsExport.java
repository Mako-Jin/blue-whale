package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分析导出任务。
 *
 * <p>{@code content} 为阶段性临时存储，接入 whale-file 后改为 fileId 引用；
 * 单次导出行数受 {@code whale.crm.analytics.export-max-rows} 约束。</p>
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

    /** CSV 内容 */
    private String content;

    /** 失败原因 */
    private String error;

    /** 发起人 */
    private Long requestedBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 标记导出完成。
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
     * 标记导出失败。
     *
     * @param message 失败原因
     */
    public void fail(String message) {
        this.status = ExportStatus.FAILED;
        this.error = message;
    }
}

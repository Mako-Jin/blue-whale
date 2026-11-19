package com.bluewhale.audit.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志异步导出任务状态（AUTH-08）。
 *
 * <p>{@code POST /audit/log/export} 提交后立即返回本对象（{@code status=PENDING}），
 * 调用方按 {@code taskId} 轮询 {@code GET /audit/log/export/{taskId}} 获取进度与上传结果，
 * 避免大导出长时间占用 HTTP 线程导致网关 504。</p>
 *
 * <p>{@code upload} 字段与 whale-file 的 {@code FileUploadVO} 对齐并附加 {@code rows} 行数；
 * 无匹配记录时为空 Map。任务状态仅保存在进程内存（保留 2 小时），重启后任务丢失，需重新提交。</p>
 *
 * @author BlueWhale
 */
@Data
public class AuditExportTaskVO {

    /** 任务ID（32 位无横线 UUID） */
    private String taskId;

    /** 任务状态：PENDING / RUNNING / SUCCESS / FAILED */
    private Status status;

    /** 导出成功时的写入行数；未完成时为 {@code null} */
    private Integer rows;

    /** 导出成功时的 whale-file 上传结果（含 objectKey/fileName/rows 等）；未完成时为 {@code null} */
    private Map<String, Object> upload;

    /** 失败原因（通用文案，不含内网地址/SQL 等实现细节）；非失败状态为 {@code null} */
    private String errorMessage;

    /** 任务创建时间 */
    private LocalDateTime createdAt;

    /** 任务完成时间；未完成时为 {@code null} */
    private LocalDateTime finishedAt;

    /**
     * 导出任务状态
     */
    public enum Status {
        /** 已提交，等待执行 */
        PENDING,
        /** 执行中 */
        RUNNING,
        /** 导出并上传成功 */
        SUCCESS,
        /** 导出失败（查询、CSV 生成或上传失败） */
        FAILED
    }
}

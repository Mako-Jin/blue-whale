package com.bluewhale.audit.retention.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志归档水位实体，对应 {@code audit_archive_watermark} 表（AUTH-09）。
 *
 * <p>归档任务<b>不删除</b> {@code audit_log}（append-only），若每轮都按「早于保留期」全量查询，
 * 同一批过期记录会被反复导出、重复上传。本表单行（{@code id} 恒为 1）记录上一轮成功归档批次的
 * 最后一条 {@code create_time + id}，下一轮据此增量续查，保证每条记录只导出一轮。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("audit_archive_watermark")
public class AuditArchiveWatermark implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 固定单行主键，恒为 1 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 已归档批次的最后一条 {@code audit_log.create_time} */
    private LocalDateTime lastCreateTime;

    /** 已归档批次的最后一条 {@code audit_log.id}（同一 {@code create_time} 内去重） */
    private Long lastId;

    /** 水位更新时间 */
    private LocalDateTime updateTime;
}

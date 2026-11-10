package com.bluewhale.archive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 档案同步游标实体，对应表 {@code archive_sync_state}。
 *
 * <p>按名称唯一保存工作流增量拉取的时间水位，服务重启后从水位继续，避免全量重扫；
 * 单次运行结果摘要用于 {@code /archive/sync/status} 运维观测。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("archive_sync_state")
public class ArchiveSyncState extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 游标名称（如 workflow_approved） */
    private String name;

    /** 已处理到的工作流实例更新时间水位 */
    private LocalDateTime cursorTime;

    /** 最近一次同步开始时间 */
    private LocalDateTime lastRunAt;

    /** 最近一次同步结果摘要 */
    private String lastResult;
}

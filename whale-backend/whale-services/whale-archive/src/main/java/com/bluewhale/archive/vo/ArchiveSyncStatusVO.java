package com.bluewhale.archive.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 档案同步运行状态视图对象。
 *
 * @author BlueWhale
 */
@Data
public class ArchiveSyncStatusVO {

    /** 是否开启定时同步 */
    private boolean enabled;

    /** 定时同步表达式 */
    private String cron;

    /** 同步游标（办结时间水位） */
    private LocalDateTime cursorTime;

    /** 最近一次同步开始时间 */
    private LocalDateTime lastRunAt;

    /** 最近一次同步结果摘要 */
    private String lastResult;

    /** 已归档条目数 */
    private long archivedCount;

    /** 待整理条目数 */
    private long pendingCount;

    /** 失败待重试来源数 */
    private long failedCount;
}

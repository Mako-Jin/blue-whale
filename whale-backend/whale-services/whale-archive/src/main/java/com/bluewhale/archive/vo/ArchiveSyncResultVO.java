package com.bluewhale.archive.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 档案同步结果视图对象。
 *
 * @author BlueWhale
 */
@Data
public class ArchiveSyncResultVO {

    /** 本轮拉取到的已通过实例数 */
    private long pulled;

    /** 自动归档（已归档）条数 */
    private long archived;

    /** 进入待整理条数 */
    private long pending;

    /** 已同步过或状态不满足而跳过条数 */
    private long skipped;

    /** 失败条数（下一轮自动重试） */
    private long failed;

    /** 本轮结束后的同步游标（去重后的办结时间水位） */
    private LocalDateTime cursorTime;

    /** 本轮开始时间 */
    private LocalDateTime startedAt;

    /** 本轮结束时间 */
    private LocalDateTime finishedAt;
}

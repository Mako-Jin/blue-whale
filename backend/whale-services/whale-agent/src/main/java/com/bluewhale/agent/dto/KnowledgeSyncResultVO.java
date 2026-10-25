package com.bluewhale.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件知识库同步结果。
 *
 * @author BlueWhale
 */
@Data
public class KnowledgeSyncResultVO {

    /** 本轮拉取条数 */
    private long pulled;

    /** 本轮新索引/重建条数 */
    private long indexed;

    /** 本轮未变更条数 */
    private long unchanged;

    /** 本轮跳过条数（删除/停用/不支持） */
    private long skipped;

    /** 本轮失败条数 */
    private long failed;

    /** 本轮结束游标 */
    private LocalDateTime cursor;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;
}

package com.bluewhale.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件知识库统计视图对象。
 *
 * @author BlueWhale
 */
@Data
public class AgentKnowledgeStatsVO {

    /** 采集总数 */
    private long total;

    /** 待处理 */
    private long pending;

    /** 索引中 */
    private long indexing;

    /** 已索引 */
    private long indexed;

    /** 已跳过 */
    private long skipped;

    /** 失败 */
    private long failed;

    /** 已写入知识块总数 */
    private long chunkCount;

    /** 增量游标 */
    private LocalDateTime cursor;

    /** 最近一次同步开始时间 */
    private LocalDateTime lastRunAt;

    /** 最近一次同步结果摘要 */
    private String lastResult;
}

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

    /** 文档采集总数 */
    private long docTotal;

    /** 文档待处理 */
    private long docPending;

    /** 文档索引中 */
    private long docIndexing;

    /** 文档已索引 */
    private long docIndexed;

    /** 文档已跳过 */
    private long docSkipped;

    /** 文档失败 */
    private long docFailed;

    /** 文档已写入知识块总数 */
    private long docChunkCount;

    /** 文档增量游标 */
    private LocalDateTime docCursor;

    /** 文档最近一次同步开始时间 */
    private LocalDateTime docLastRunAt;

    /** 文档最近一次同步结果摘要 */
    private String docLastResult;
}

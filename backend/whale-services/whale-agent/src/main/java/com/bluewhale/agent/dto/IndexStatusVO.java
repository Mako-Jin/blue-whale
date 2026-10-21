package com.bluewhale.agent.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 索引状态视图对象（{@code GET /agent/index/status} 响应）。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class IndexStatusVO {

    /** 构建状态：READY / BUILDING / FAILED / EMPTY */
    private String status;

    /** 索引版本 */
    private String indexVersion;

    /** 已索引文件数 */
    private Integer fileCount;

    /** 已索引知识块数 */
    private Integer chunkCount;

    /** 最近构建时间 */
    private LocalDateTime lastBuildAt;

    /** 最近构建耗时（毫秒） */
    private Long buildMillis;

    /** 索引目录（便于运维排查） */
    private String indexDir;
}

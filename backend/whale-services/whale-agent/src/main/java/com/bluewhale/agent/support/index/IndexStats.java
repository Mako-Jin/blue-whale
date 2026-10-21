package com.bluewhale.agent.support.index;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 索引统计（进程内实时值，供状态接口与前端徽标使用）。
 *
 * @author BlueWhale
 */
@Getter
@Builder
public class IndexStats {

    /** 已索引文件数 */
    private final int fileCount;

    /** 已索引知识块数 */
    private final int chunkCount;

    /** 最近一次构建完成时间 */
    private final LocalDateTime lastBuildAt;

    /** 构建状态：READY / BUILDING / FAILED / EMPTY */
    private final String status;

    /** 最近一次构建耗时（毫秒） */
    private final Long buildMillis;
}

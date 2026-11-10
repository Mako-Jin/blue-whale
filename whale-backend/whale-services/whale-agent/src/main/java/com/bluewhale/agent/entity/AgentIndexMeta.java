package com.bluewhale.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 索引元数据实体，对应表 {@code agent_index_meta}（单行表，仅保留最新一次构建记录）。
 *
 * <p>记录索引版本、文件数、块数、最近构建时间与状态，供 {@code GET /agent/index/status}
 * 与前端顶部索引徽标使用；{@code dimension} 变更（换 embedding 模型）须全量重建。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_index_meta")
public class AgentIndexMeta extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 索引版本（构建批次标识，如 20261021103000） */
    private String indexVersion;

    /** 已索引文件数 */
    private Integer fileCount;

    /** 已索引知识块数 */
    private Integer chunkCount;

    /** 最近一次构建完成时间 */
    private LocalDateTime lastBuildAt;

    /** 构建状态：READY / BUILDING / FAILED / EMPTY */
    private String status;

    /** 向量维度（未启用向量为 0） */
    private Integer dimension;

    /** 最近一次构建耗时（毫秒） */
    private Long buildMillis;
}

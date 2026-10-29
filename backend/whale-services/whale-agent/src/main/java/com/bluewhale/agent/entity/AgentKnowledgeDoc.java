package com.bluewhale.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文档知识库采集台账实体，对应表 {@code agent_knowledge_doc}。
 *
 * <p>一行对应 whale-doc 中的一个页面；{@code pageId} 唯一保证幂等，
 * {@code contentHash} 变化触发重建，{@code sourceUpdateTime} 兼作增量变更游标。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_knowledge_doc")
public class AgentKnowledgeDoc extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 文档页面ID（doc_page.id） */
    private Long pageId;

    /** 文档空间ID */
    private Long spaceId;

    /** 页面标题 */
    private String title;

    /** 页面路径 */
    private String slug;

    /** 页面版本号 */
    private Long revision;

    /** 正文哈希（变化触发重建） */
    private String contentHash;

    /** 页面地址（检索结果跳转） */
    private String url;

    /** 状态：PENDING / INDEXING / INDEXED / SKIPPED / FAILED */
    private String indexStatus;

    /** 已写入知识块数 */
    private Integer chunkCount;

    /** 失败尝试次数 */
    private Integer attempts;

    /** 下次重试时间（指数退避） */
    private LocalDateTime nextRetryAt;

    /** 最近失败原因（截断） */
    private String lastError;

    /** 最近成功索引时间 */
    private LocalDateTime indexedAt;

    /** 是否启用：1 启用 / 0 停用 */
    private Integer enabled;

    /** 页面更新时间（增量游标） */
    private LocalDateTime sourceUpdateTime;
}

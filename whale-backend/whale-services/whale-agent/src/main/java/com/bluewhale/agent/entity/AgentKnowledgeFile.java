package com.bluewhale.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件知识库采集台账实体，对应表 {@code agent_knowledge_file}。
 *
 * <p>一行对应文件服务中的一个业务文件；{@code fileId} 唯一保证幂等，
 * {@code md5} 变化触发重建，{@code sourceUpdateTime} 兼作增量变更游标。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_knowledge_file")
public class AgentKnowledgeFile extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 文件服务文件ID（file_info.id） */
    private Long fileId;

    /** 对象存储桶 */
    private String bucket;

    /** 原始文件名 */
    private String originalName;

    /** 文件MIME类型 */
    private String contentType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件MD5摘要（变化触发重建） */
    private String md5;

    /** 上传用户ID */
    private Long uploaderId;

    /** 知识来源：FILE 业务文件 / ARCHIVE 档案快照 */
    private String source;

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

    /** 文件更新时间（增量游标） */
    private LocalDateTime sourceUpdateTime;
}

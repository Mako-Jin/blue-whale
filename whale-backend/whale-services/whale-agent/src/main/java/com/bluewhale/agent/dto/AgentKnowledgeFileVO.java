package com.bluewhale.agent.dto;

import com.bluewhale.agent.entity.AgentKnowledgeFile;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件知识库采集台账视图对象。
 *
 * @author BlueWhale
 */
@Data
public class AgentKnowledgeFileVO {

    /** 台账ID */
    private Long id;

    /** 文件ID */
    private Long fileId;

    /** 对象存储桶 */
    private String bucket;

    /** 原始文件名 */
    private String originalName;

    /** MIME 类型 */
    private String contentType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MD5 摘要 */
    private String md5;

    /** 上传用户ID */
    private Long uploaderId;

    /** 来源：FILE / ARCHIVE */
    private String source;

    /** 状态：PENDING/INDEXING/INDEXED/SKIPPED/FAILED */
    private String indexStatus;

    /** 知识块数 */
    private Integer chunkCount;

    /** 失败尝试次数 */
    private Integer attempts;

    /** 最近失败原因 */
    private String lastError;

    /** 最近索引时间 */
    private LocalDateTime indexedAt;

    /** 是否启用：1 启用 / 0 停用 */
    private Integer enabled;

    /** 文件更新时间（游标） */
    private LocalDateTime sourceUpdateTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 由实体转换视图对象。
     *
     * @param entity 台账实体
     * @return 视图对象
     */
    public static AgentKnowledgeFileVO of(AgentKnowledgeFile entity) {
        AgentKnowledgeFileVO vo = new AgentKnowledgeFileVO();
        vo.setId(entity.getId());
        vo.setFileId(entity.getFileId());
        vo.setBucket(entity.getBucket());
        vo.setOriginalName(entity.getOriginalName());
        vo.setContentType(entity.getContentType());
        vo.setFileSize(entity.getFileSize());
        vo.setMd5(entity.getMd5());
        vo.setUploaderId(entity.getUploaderId());
        vo.setSource(entity.getSource());
        vo.setIndexStatus(entity.getIndexStatus());
        vo.setChunkCount(entity.getChunkCount());
        vo.setAttempts(entity.getAttempts());
        vo.setLastError(entity.getLastError());
        vo.setIndexedAt(entity.getIndexedAt());
        vo.setEnabled(entity.getEnabled());
        vo.setSourceUpdateTime(entity.getSourceUpdateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}

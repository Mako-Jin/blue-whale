package com.bluewhale.agent.dto;

import com.bluewhale.agent.entity.AgentKnowledgeDoc;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档知识库采集台账视图对象。
 *
 * @author BlueWhale
 */
@Data
public class AgentKnowledgeDocVO {

    /** 台账ID */
    private Long id;

    /** 页面ID */
    private Long pageId;

    /** 空间ID */
    private Long spaceId;

    /** 页面标题 */
    private String title;

    /** 页面路径 */
    private String slug;

    /** 页面版本号 */
    private Long revision;

    /** 页面地址 */
    private String url;

    /** 状态 */
    private String indexStatus;

    /** 已写入知识块数 */
    private Integer chunkCount;

    /** 失败尝试次数 */
    private Integer attempts;

    /** 最近失败原因 */
    private String lastError;

    /** 最近成功索引时间 */
    private LocalDateTime indexedAt;

    /** 是否启用 */
    private Integer enabled;

    /** 页面更新时间 */
    private LocalDateTime updateTime;

    /**
     * 转换为视图对象。
     *
     * @param entity 台账实体
     * @return 视图对象
     */
    public static AgentKnowledgeDocVO of(AgentKnowledgeDoc entity) {
        AgentKnowledgeDocVO vo = new AgentKnowledgeDocVO();
        vo.setId(entity.getId());
        vo.setPageId(entity.getPageId());
        vo.setSpaceId(entity.getSpaceId());
        vo.setTitle(entity.getTitle());
        vo.setSlug(entity.getSlug());
        vo.setRevision(entity.getRevision());
        vo.setUrl(entity.getUrl());
        vo.setIndexStatus(entity.getIndexStatus());
        vo.setChunkCount(entity.getChunkCount());
        vo.setAttempts(entity.getAttempts());
        vo.setLastError(entity.getLastError());
        vo.setIndexedAt(entity.getIndexedAt());
        vo.setEnabled(entity.getEnabled());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}

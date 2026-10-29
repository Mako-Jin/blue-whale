package com.bluewhale.agent.support.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档服务返回的页面变更/快照（知识库采集用）。
 *
 * @author BlueWhale
 */
@Data
public class RemoteDocChange {

    /** 页面ID */
    private Long pageId;

    /** 空间ID */
    private Long spaceId;

    /** 空间名称 */
    private String spaceName;

    /** 标题 */
    private String title;

    /** 页面路径 */
    private String slug;

    /** 页面状态：DRAFT / PUBLISHED / ARCHIVED / PENDING_APPROVAL */
    private String status;

    /** 页面版本号 */
    private Long revision;

    /** 是否启用页面级权限（启用后不采集） */
    private Boolean restricted;

    /** 是否已删除（墓碑） */
    private Boolean deleted;

    /** 空间可见性：PUBLIC / MEMBER */
    private String spaceVisibility;

    /** 空间是否启用且未删除 */
    private Boolean spaceActive;

    /** 纯文本投影（仅快照返回） */
    private String contentText;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 是否为可采集页面：未删除、已发布、公开空间、空间启用且未启用页面级权限。
     *
     * @return 可采集返回 {@code true}
     */
    public boolean collectable() {
        return !Boolean.TRUE.equals(deleted) && !Boolean.TRUE.equals(restricted)
                && KnowledgeConstants.DOC_STATUS_PUBLISHED.equals(status)
                && Boolean.TRUE.equals(spaceActive)
                && KnowledgeConstants.DOC_SPACE_PUBLIC.equals(spaceVisibility);
    }
}

package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识源增量变更项（含删除/归档墓碑）。
 *
 * @author BlueWhale
 */
@Data
public class DocInternalChangeVO implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** 状态 */
    private String status;

    /** revision */
    private Long revision;

    /** 是否启用页面级权限（启用后不由 AI 知识源采集） */
    private boolean restricted;

    /** 是否已删除 */
    private boolean deleted;

    /** 空间可见性：PUBLIC / PRIVATE */
    private String spaceVisibility;

    /** 空间是否启用且未删除 */
    private boolean spaceActive;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

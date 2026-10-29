package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识源页面快照。
 *
 * @author BlueWhale
 */
@Data
public class DocInternalPageVO implements Serializable {

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

    /** 空间可见性：PUBLIC / MEMBER */
    private String spaceVisibility;

    /** 空间是否启用且未删除 */
    private Boolean spaceActive;

    /** revision */
    private Long revision;

    /** 纯文本投影 */
    private String contentText;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

package com.bluewhale.doc.infrastructure.persistence.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最近访问行。
 *
 * @author BlueWhale
 */
@Data
public class DocRecentRow {

    /** 页面ID */
    private Long pageId;

    /** 标题 */
    private String title;

    /** 空间ID */
    private Long spaceId;

    /** 空间名称 */
    private String spaceName;

    /** 页面路径 */
    private String slug;

    /** 最近访问时间 */
    private LocalDateTime visitedAt;

    /** 页面更新时间 */
    private LocalDateTime updateTime;
}

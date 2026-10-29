package com.bluewhale.doc.infrastructure.persistence.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识源增量同步行（允许包含逻辑删除与归档墓碑）。
 *
 * @author BlueWhale
 */
@Data
public class DocPageChangeRow {

    /** 页面ID */
    private Long pageId;

    /** 空间ID */
    private Long spaceId;

    /** 空间名称 */
    private String spaceName;

    /** 页面标题 */
    private String title;

    /** 页面路径 */
    private String slug;

    /** 页面状态 */
    private String status;

    /** revision */
    private Long revision;

    /** 是否启用页面级权限：0 否 / 1 是 */
    private Integer isRestricted;

    /** 逻辑删除标记：0 未删除 / 1 已删除 */
    private Integer deleted;

    /** 空间可见性 */
    private String visibility;

    /** 空间状态：1 启用 / 0 停用 */
    private Integer spaceStatus;

    /** 空间逻辑删除：0 未删除 / 1 已删除 */
    private Integer spaceDeleted;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

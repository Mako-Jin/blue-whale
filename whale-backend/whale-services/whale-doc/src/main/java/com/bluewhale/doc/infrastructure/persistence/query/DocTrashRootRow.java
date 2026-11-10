package com.bluewhale.doc.infrastructure.persistence.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站根节点查询行（M2）。
 *
 * @author BlueWhale
 */
@Data
public class DocTrashRootRow {

    /** 租户ID（自动清理任务跨租户查询时使用） */
    private Long tenantId;

    /** 页面ID（回收站根节点） */
    private Long pageId;

    /** 页面标题 */
    private String title;

    /** 节点类型：PAGE / FOLDER */
    private String pageType;

    /** 原父页面ID */
    private Long parentId;

    /** 原父页面标题；父页面已删除或不存在时为 {@code null} */
    private String parentTitle;

    /** 删除操作人ID */
    private Long deletedBy;

    /** 移入回收站时间 */
    private LocalDateTime deletedAt;

    /** 子树内除根节点外的页面数量 */
    private Long childCount;
}

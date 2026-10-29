package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 回收站条目视图（M2）。
 *
 * <p>回收站列表只展示子树根节点，{@code childCount} 表示其下子孙页面数量。</p>
 *
 * @author BlueWhale
 */
@Data
public class DocRecycleItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

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

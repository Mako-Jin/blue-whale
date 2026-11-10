package com.bluewhale.doc.infrastructure.persistence.query;

import lombok.Data;

/**
 * 回收站子树页面轻量行（M2）。
 *
 * <p>用于恢复与彻底清除：只读取定位、层级与 slug 重算所需字段。</p>
 *
 * @author BlueWhale
 */
@Data
public class DocTrashPageRow {

    /** 页面ID */
    private Long pageId;

    /** 父页面ID */
    private Long parentId;

    /** 页面标题 */
    private String title;

    /** 页面路径（删除后带 trash 后缀） */
    private String slug;

    /** 节点类型：PAGE / FOLDER */
    private String pageType;

    /** 空间ID */
    private Long spaceId;
}

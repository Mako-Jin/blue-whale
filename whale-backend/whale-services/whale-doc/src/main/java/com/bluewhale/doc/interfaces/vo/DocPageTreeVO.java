package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 页面树节点。
 *
 * @author BlueWhale
 */
@Data
public class DocPageTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long id;

    /** 父页面ID */
    private Long parentId;

    /** 标题 */
    private String title;

    /** 页面路径 */
    private String slug;

    /** 节点类型 */
    private String pageType;

    /** 状态 */
    private String status;

    /** 图标 */
    private String icon;

    /** 排序 */
    private Long sortNo;

    /** 是否启用页面级权限（M2-4） */
    private boolean restricted;

    /** 子节点 */
    private List<DocPageTreeVO> children = new ArrayList<>();
}

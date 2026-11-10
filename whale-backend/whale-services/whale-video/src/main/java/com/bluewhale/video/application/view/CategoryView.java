package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;

/**
 * 分类视图。
 *
 * @author BlueWhale
 */
@Data
public class CategoryView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类 ID */
    private Long id;

    /** 父分类 ID，0 表示顶级 */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 分类编码 */
    private String code;

    /** 适用范围 */
    private String contentTypeScope;

    /** 排序 */
    private Integer sortNo;
}

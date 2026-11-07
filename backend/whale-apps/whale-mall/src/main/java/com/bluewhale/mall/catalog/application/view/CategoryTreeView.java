package com.bluewhale.mall.catalog.application.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 类目树节点。
 *
 * @author BlueWhale
 */
@Data
public class CategoryTreeView {

    /** 类目 ID */
    private Long id;

    /** 上级类目 ID（0 为一级） */
    private Long parentId;

    /** 类目名称 */
    private String categoryName;

    /** 层级 */
    private Integer categoryLevel;

    /** 排序号 */
    private Integer sortNo;

    /** 状态 */
    private String status;

    /** 下级类目 */
    private List<CategoryTreeView> children = new ArrayList<>();
}

package com.bluewhale.mall.catalog.domain.model;

import lombok.Data;

/**
 * 类目查询条件。
 *
 * @author BlueWhale
 */
@Data
public class CategoryQuery {

    /** 关键字（名称模糊） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 上级类目 ID */
    private Long parentId;
}

package com.bluewhale.mall.catalog.domain.model;

import lombok.Data;

/**
 * 商品查询条件。
 *
 * @author BlueWhale
 */
@Data
public class SpuQuery {

    /** 关键字（商品编码或名称模糊） */
    private String keyword;

    /** 类目 ID */
    private Long categoryId;

    /** 状态 */
    private String status;

    /** 页码（从 1 起） */
    private Long current;

    /** 每页条数 */
    private Long size;
}

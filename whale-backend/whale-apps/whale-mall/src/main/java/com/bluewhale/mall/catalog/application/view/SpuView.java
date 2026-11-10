package com.bluewhale.mall.catalog.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品列表行视图（不含 SKU，列表页只需要封面与状态）。
 *
 * @author BlueWhale
 */
@Data
public class SpuView {

    /** 商品 ID */
    private Long id;

    /** 商品编码 */
    private String spuCode;

    /** 商品名称 */
    private String spuName;

    /** 类目 ID */
    private Long categoryId;

    /** 类目名称 */
    private String categoryName;

    /** 品牌 */
    private String brand;

    /** 主图 */
    private String mainImage;

    /** 状态 */
    private String status;

    /** 排序号 */
    private Integer sortNo;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

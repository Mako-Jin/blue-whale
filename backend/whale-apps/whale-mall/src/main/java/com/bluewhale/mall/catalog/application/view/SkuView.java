package com.bluewhale.mall.catalog.application.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * SKU 视图（含价目与库存）。
 *
 * @author BlueWhale
 */
@Data
public class SkuView {

    /** SKU ID */
    private Long id;

    /** SKU 编码 */
    private String skuCode;

    /** 规格描述 */
    private String specJson;

    /** SKU 图 */
    private String imageUrl;

    /** 状态 */
    private String status;

    /** 排序号 */
    private Integer sortNo;

    /** 可售数量 */
    private Integer availableQty;

    /** 已预占数量 */
    private Integer reservedQty;

    /** 价目 */
    private List<PriceView> prices = new ArrayList<>();
}

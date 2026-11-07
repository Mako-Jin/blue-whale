package com.bluewhale.mall.catalog.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存行视图。
 *
 * @author BlueWhale
 */
@Data
public class StockView {

    /** SKU ID */
    private Long skuId;

    /** SKU 编码 */
    private String skuCode;

    /** 商品 ID */
    private Long spuId;

    /** 商品名称 */
    private String spuName;

    /** 规格描述 */
    private String specJson;

    /** 商品状态 */
    private String spuStatus;

    /** 可售数量 */
    private Integer availableQty;

    /** 已预占数量 */
    private Integer reservedQty;

    /** 在库总量（可售 + 预占） */
    private Integer totalQty;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

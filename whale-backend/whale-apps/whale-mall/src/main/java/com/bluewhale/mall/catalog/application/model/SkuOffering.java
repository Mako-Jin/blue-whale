package com.bluewhale.mall.catalog.application.model;

import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.model.SpuStatus;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 可售单元（某 SKU 在某个商城形态下的一次解析结果）。
 *
 * <p>把「商品是否在售 / 规格是否启用 / 该形态有没有价 / 还有多少库存」四件事收在一处，
 * 购物车与下单共用同一份判定。分开各写一遍的结果一定是「购物车显示可买、下单报不可买」。</p>
 *
 * @author BlueWhale
 */
@Data
public class SkuOffering {

    /** SKU */
    private Sku sku;

    /** 所属商品 ID */
    private Long spuId;

    /** 商品名称 */
    private String spuName;

    /** 商品类目 ID（订单落快照，品类券判定用） */
    private Long categoryId;

    /** 商品主图 */
    private String mainImage;

    /** 商品状态 */
    private SpuStatus spuStatus;

    /** 成交价目类型（不可售时为空） */
    private String priceType;

    /** 成交单价（不可售时为空） */
    private BigDecimal unitPrice;

    /** 会员价覆盖前的原价（仅会员价命中时非空，订单行原价快照用） */
    private BigDecimal listPrice;

    /** 可售数量 */
    private Integer availableQty;

    /** 是否可购买 */
    private boolean available;

    /** 不可购买原因 */
    private String reason;
}

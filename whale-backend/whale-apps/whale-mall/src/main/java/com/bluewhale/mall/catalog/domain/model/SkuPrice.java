package com.bluewhale.mall.catalog.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 价目（一种价目一行）。
 *
 * @author BlueWhale
 */
@Data
public class SkuPrice {

    /** 价目类型 */
    private PriceType priceType;

    /** 价格（按价目类型解释为元或积分） */
    private BigDecimal price;
}

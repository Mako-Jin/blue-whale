package com.bluewhale.mall.catalog.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 价目视图。
 *
 * @author BlueWhale
 */
@Data
public class PriceView {

    /** 价目类型 */
    private String priceType;

    /** 价格 */
    private BigDecimal price;
}

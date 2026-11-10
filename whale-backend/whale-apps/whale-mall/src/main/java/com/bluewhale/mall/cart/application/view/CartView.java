package com.bluewhale.mall.cart.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 购物车视图。
 *
 * @author BlueWhale
 */
@Data
public class CartView {

    /** 渠道 ID */
    private Long channelId;

    /** 渠道名称 */
    private String channelName;

    /** 金额口径：CASH / POINTS */
    private String currencyType;

    /** 条目列表 */
    private List<CartItemView> items = new ArrayList<>();

    /** 总件数（全部条目） */
    private Integer totalQuantity;

    /** 已选中件数 */
    private Integer selectedQuantity;

    /** 已选中金额（仅统计可购买条目） */
    private BigDecimal selectedAmount;
}

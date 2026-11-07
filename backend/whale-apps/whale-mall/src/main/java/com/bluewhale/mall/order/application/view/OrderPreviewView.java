package com.bluewhale.mall.order.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 下单试算视图。
 *
 * @author BlueWhale
 */
@Data
public class OrderPreviewView {

    /** 渠道 ID */
    private Long channelId;

    /** 商城形态 */
    private String mode;

    /** 结算方式 */
    private String settlementType;

    /** 金额口径 */
    private String currencyType;

    /** 明细 */
    private List<OrderLineView> lines = new ArrayList<>();

    /** 合计金额 */
    private BigDecimal totalAmount;

    /** 合计件数 */
    private Integer totalQuantity;
}

package com.bluewhale.mall.order.application.view;

import com.bluewhale.mall.coupon.application.view.CouponUserView;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 下单试算视图。
 *
 * <p>金额拆成四项回给前端，而不是只给一个合计：买家要看到「商品 100 - 优惠 20 + 运费 0」，
 * 只给 80 的话，任何一次对不上都会变成客服工单。</p>
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

    /** 商品总额 */
    private BigDecimal goodsAmount;

    /** 原价合计（未含活动与会员让利，对账口径） */
    private BigDecimal listAmount;

    /** 活动与会员价让利合计 */
    private BigDecimal activityDiscount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 应付金额 */
    private BigDecimal totalAmount;

    /** 合计件数 */
    private Integer totalQuantity;

    /** 本次选中的主券用户券 ID（兼容口径，多券明细见 coupons） */
    private Long couponId;

    /** 本次选中的主券名称（含义同 couponId） */
    private String couponName;

    /** 本次选中的用券明细（一单多券，逐张生效优惠） */
    private List<OrderCouponView> coupons = new ArrayList<>();

    /** 当前订单可用的券（含不可用及原因） */
    private List<CouponUserView> availableCoupons = new ArrayList<>();
}

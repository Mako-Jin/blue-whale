package com.bluewhale.mall.common.enums;

import com.bluewhale.mall.catalog.domain.model.PriceType;

import java.util.Locale;

/**
 * 商城形态（渠道级）。
 *
 * <p>一套内核服务三种形态：形态只决定「这一单按哪种价目计价」，不决定业务表结构。
 * 新增第四种形态（例如「积分+现金混合」）只需在这里加枚举值与一条价目映射，
 * 不新增表、不复制订单逻辑。</p>
 *
 * @author BlueWhale
 */
public enum MallMode {

    /** 零售：面向公众消费者，按零售价成交 */
    RETAIL,

    /** 经销订货：经销商/客户自助下单，按经销价成交（缺失时回落零售价） */
    B2B,

    /** 员工福利：内部员工用福利积分兑换，按积分价成交（缺失即失败） */
    WELFARE;

    /**
     * 该形态优先使用的价目类型。
     *
     * @return 价目类型
     */
    public PriceType priceType() {
        return switch (this) {
            case B2B -> PriceType.B2B;
            case WELFARE -> PriceType.POINTS;
            default -> PriceType.RETAIL;
        };
    }

    /**
     * 该形态允许的兜底价目。
     *
     * <p>只有经销订货允许回落零售价——未签约客户按零售价成交是常见商业规则；
     * 零售与福利<b>不允许</b>任何回落：把 99 元当成 99 积分卖出去不是「兜底」，是事故。</p>
     *
     * @return 兜底价目；不允许兜底时返回 null
     */
    public PriceType fallbackPriceType() {
        return this == B2B ? PriceType.RETAIL : null;
    }

    /**
     * 解析形态文本。
     *
     * @param value 形态文本
     * @return 形态；无法识别时返回 null（由调用方判定为非法入参，不静默回落零售）
     */
    public static MallMode parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

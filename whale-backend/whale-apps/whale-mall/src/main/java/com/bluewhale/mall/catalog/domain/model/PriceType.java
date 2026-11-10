package com.bluewhale.mall.catalog.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.CurrencyType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * 价目类型。
 *
 * <p>一种价目一行，而不是「零售价 / 经销价 / 积分价」三个列：加一种价目
 * （例如「员工内购价」）只需要加枚举值，不用改表、不用改所有 DTO。</p>
 *
 * <p>积分为整数：0.5 积分没有业务含义，允许它只会让对账时出现无法解释的小数。</p>
 *
 * @author BlueWhale
 */
public enum PriceType {

    /** 零售价（元） */
    RETAIL(CurrencyType.CASH),

    /** 经销价（元） */
    B2B(CurrencyType.CASH),

    /** 会员价（元，仅零售形态生效；登记在 {@code mall_member} 的用户在有效期内按此价成交） */
    MEMBER(CurrencyType.CASH),

    /** 福利积分价（积分） */
    POINTS(CurrencyType.POINTS);

    /** 该价目对应的金额口径 */
    private final CurrencyType currencyType;

    /**
     * 构造价目类型。
     *
     * @param currencyType 金额口径
     */
    PriceType(CurrencyType currencyType) {
        this.currencyType = currencyType;
    }

    /**
     * 查询该价目对应的金额口径。
     *
     * @return 金额口径
     */
    public CurrencyType currencyType() {
        return currencyType;
    }

    /**
     * 是否为积分价。
     *
     * @return 积分价返回 true
     */
    public boolean isPoints() {
        return currencyType == CurrencyType.POINTS;
    }

    /**
     * 规范化价格：保留两位小数；积分价必须为正整数。
     *
     * @param price 原始价格
     * @return 规范化后的价格
     */
    public BigDecimal normalize(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw MallErrorCode.exception(MallErrorCode.PRICE_INVALID, "价格必须大于 0");
        }
        BigDecimal scaled = price.setScale(2, RoundingMode.HALF_UP);
        if (isPoints() && scaled.stripTrailingZeros().scale() > 0) {
            throw MallErrorCode.exception(MallErrorCode.PRICE_INVALID, "积分价必须为整数");
        }
        return scaled;
    }

    /**
     * 解析价目类型文本。
     *
     * @param value 文本
     * @return 价目类型；无法识别时返回 null
     */
    public static PriceType parse(String value) {
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

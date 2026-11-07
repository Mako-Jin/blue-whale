package com.bluewhale.mall.catalog.domain.model;

import com.bluewhale.mall.common.MallErrorCode;

import java.util.Locale;

/**
 * 商品状态。
 *
 * <p>只有 {@link #ON_SALE} 能下单。{@link #DRAFT} 是「还没配好价格和库存」的草稿，
 * {@link #OFF_SALE} 是「主动下架」。两者的区别不在买家侧（都看不到），
 * 而在运营侧：草稿可以随意删，下架商品可能还有在途售后。</p>
 *
 * @author BlueWhale
 */
public enum SpuStatus {

    /** 草稿 */
    DRAFT,

    /** 已上架 */
    ON_SALE,

    /** 已下架 */
    OFF_SALE;

    /**
     * 是否可切换为指定状态。
     *
     * <p>允许的边：DRAFT → ON_SALE、OFF_SALE → ON_SALE、ON_SALE → OFF_SALE。
     * 不允许 ON_SALE → DRAFT：回到草稿会让人误以为「还没卖过」，而它可能已经有订单。</p>
     *
     * @param target 目标状态
     * @return 允许切换返回 true
     */
    public boolean canTransitionTo(SpuStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (target) {
            case ON_SALE -> this == DRAFT || this == OFF_SALE;
            case OFF_SALE -> this == ON_SALE;
            default -> false;
        };
    }

    /**
     * 校验状态切换，非法时抛出业务异常。
     *
     * @param target 目标状态
     */
    public void assertTransitionTo(SpuStatus target) {
        if (!canTransitionTo(target)) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_STATE_CONFLICT,
                    "商品当前状态（" + this + "）不允许切换为 " + target);
        }
    }

    /**
     * 解析状态文本。
     *
     * @param value 文本，可为 null
     * @return 状态；无法识别时回落到 {@link #DRAFT}
     */
    public static SpuStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DRAFT;
        }
    }
}

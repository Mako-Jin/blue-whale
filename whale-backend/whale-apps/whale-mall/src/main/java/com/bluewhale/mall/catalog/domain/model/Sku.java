package com.bluewhale.mall.catalog.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.MallMode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 商品规格（SKU）。
 *
 * <p>SKU 编码在租户内<b>全局唯一</b>，不是 SPU 内唯一：拣货、对账、售后都按 SKU 编码
 * 单点定位，允许两个商品撞码等于埋雷。</p>
 *
 * @author BlueWhale
 */
@Data
public class Sku {

    /** 编码最大长度 */
    public static final int CODE_MAX_LENGTH = 64;

    /** 规格描述最大长度 */
    public static final int SPEC_MAX_LENGTH = 512;

    /** 主键（新建时为 null） */
    private Long id;

    /** 所属商品 ID */
    private Long spuId;

    /** SKU 编码（租户内唯一） */
    private String skuCode;

    /** 规格描述（JSON 文本） */
    private String specJson;

    /** SKU 图 */
    private String imageUrl;

    /** 单件重量（kg，按重运费计量用；可空，空则按重模板退回首重价） */
    private BigDecimal weightKg;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 排序号 */
    private Integer sortNo;

    /** 价目列表 */
    private List<SkuPrice> prices = new ArrayList<>();

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 是否启用。
     *
     * @return 启用返回 true
     */
    public boolean isEnabled() {
        return status == null || "ENABLED".equalsIgnoreCase(status);
    }

    /**
     * 校验 SKU 自身字段。
     */
    public void validate() {
        if (skuCode == null || skuCode.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID, "SKU 编码不能为空");
        }
        if (skuCode.trim().length() > CODE_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID,
                    "SKU 编码不得超过 " + CODE_MAX_LENGTH + " 字符");
        }
        if (specJson != null && specJson.length() > SPEC_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID,
                    "规格描述不得超过 " + SPEC_MAX_LENGTH + " 字符");
        }
        skuCode = skuCode.trim().toUpperCase(Locale.ROOT);
        if (weightKg != null && weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID, "单件重量必须大于 0（kg），不计量请留空");
        }
        if (status == null || status.isBlank()) {
            status = "ENABLED";
        }
    }

    /**
     * 查询指定类型的价目。
     *
     * @param priceType 价目类型
     * @return 价目；未配置返回 null
     */
    public SkuPrice findPrice(PriceType priceType) {
        if (priceType == null || prices == null) {
            return null;
        }
        for (SkuPrice candidate : prices) {
            if (candidate.getPriceType() == priceType) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 按商城形态解析成交价。
     *
     * <p>解析顺序：形态对应价目 → 形态允许的兜底价目（仅 B2B 回落零售价）。
     * 都没有则抛 {@link MallErrorCode#PRICE_MISSING}——<b>绝不返回 0</b>：
     * 0 元订单会正常落库、正常发货，事后只能靠人工翻账找出来。</p>
     *
     * @param mode 商城形态
     * @return 命中的价目
     */
    public SkuPrice resolvePrice(MallMode mode) {
        MallMode safeMode = mode == null ? MallMode.RETAIL : mode;
        SkuPrice primary = findPrice(safeMode.priceType());
        if (primary != null) {
            return primary;
        }
        SkuPrice fallback = findPrice(safeMode.fallbackPriceType());
        if (fallback != null) {
            return fallback;
        }
        throw MallErrorCode.exception(MallErrorCode.PRICE_MISSING,
                "SKU " + skuCode + " 未配置 " + safeMode.priceType() + " 价目");
    }

    /**
     * 按商城形态解析成交价（含会员价口径）。
     *
     * <p>零售形态下 {@code memberPrice} 为 true 且配置了 {@link PriceType#MEMBER}
     * 价目时按会员价成交；其他形态或未配置会员价目一律回落到
     * {@link #resolvePrice(MallMode)} 的普通解析（绝不因会员身份提价或报错）。</p>
     *
     * @param mode        商城形态
     * @param memberPrice 下单用户是否为有效会员
     * @return 命中的价目
     */
    public SkuPrice resolvePrice(MallMode mode, boolean memberPrice) {
        MallMode safeMode = mode == null ? MallMode.RETAIL : mode;
        if (memberPrice && safeMode.priceType() == PriceType.RETAIL) {
            SkuPrice member = findPrice(PriceType.MEMBER);
            if (member != null) {
                return member;
            }
        }
        return resolvePrice(mode);
    }

    /**
     * 按类型查询价格，缺失即失败（用于校验必需价目）。
     *
     * @param priceType 价目类型
     * @return 价格
     */
    public BigDecimal requirePrice(PriceType priceType) {
        SkuPrice price = findPrice(priceType);
        if (price == null) {
            throw MallErrorCode.exception(MallErrorCode.PRICE_MISSING,
                    "SKU " + skuCode + " 未配置 " + priceType + " 价目");
        }
        return price.getPrice();
    }
}

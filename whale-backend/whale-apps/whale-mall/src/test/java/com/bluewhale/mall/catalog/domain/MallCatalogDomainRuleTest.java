package com.bluewhale.mall.catalog.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.catalog.domain.model.PriceType;
import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.model.SkuPrice;
import com.bluewhale.mall.catalog.domain.model.SkuStock;
import com.bluewhale.mall.catalog.domain.model.Spu;
import com.bluewhale.mall.catalog.domain.model.SpuStatus;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.MallMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商品域规则测试：价目解析、状态机、库存守卫。
 *
 * @author BlueWhale
 */
class MallCatalogDomainRuleTest {

    /**
     * 零售渠道只认零售价。
     */
    @Test
    @DisplayName("零售形态按 RETAIL 价成交")
    void retailModeUsesRetailPrice() {
        Sku sku = sku(new BigDecimal("99.00"), null, null);
        SkuPrice price = sku.resolvePrice(MallMode.RETAIL);
        assertThat(price.getPriceType()).isEqualTo(PriceType.RETAIL);
        assertThat(price.getPrice()).isEqualByComparingTo("99.00");
    }

    /**
     * 经销价存在时优先使用经销价。
     */
    @Test
    @DisplayName("经销形态优先 B2B 价")
    void b2bModePrefersB2bPrice() {
        Sku sku = sku(new BigDecimal("99.00"), new BigDecimal("80.00"), null);
        assertThat(sku.resolvePrice(MallMode.B2B).getPrice()).isEqualByComparingTo("80.00");
    }

    /**
     * 经销价缺失时回落零售价（未签约客户按零售价成交）。
     */
    @Test
    @DisplayName("经销价缺失回落零售价")
    void b2bModeFallsBackToRetail() {
        Sku sku = sku(new BigDecimal("99.00"), null, null);
        SkuPrice price = sku.resolvePrice(MallMode.B2B);
        assertThat(price.getPriceType()).isEqualTo(PriceType.RETAIL);
    }

    /**
     * 福利价缺失必须失败，不能拿零售价当积分价。
     */
    @Test
    @DisplayName("福利价缺失直接失败，不回落零售价")
    void welfareModeNeverFallsBack() {
        Sku sku = sku(new BigDecimal("99.00"), new BigDecimal("80.00"), null);
        assertThatThrownBy(() -> sku.resolvePrice(MallMode.WELFARE))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.PRICE_MISSING));
    }

    /**
     * 积分价必须为整数。
     */
    @Test
    @DisplayName("积分价带小数被拒绝")
    void pointsPriceMustBeInteger() {
        assertThatThrownBy(() -> PriceType.POINTS.normalize(new BigDecimal("10.5")))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.PRICE_INVALID));
        assertThat(PriceType.POINTS.normalize(new BigDecimal("10"))).isEqualByComparingTo("10.00");
    }

    /**
     * 价格必须为正，且保留两位小数。
     */
    @Test
    @DisplayName("价格规范化：正数保留两位，零与负数拒绝")
    void normalizePrice() {
        assertThat(PriceType.RETAIL.normalize(new BigDecimal("9.999"))).isEqualByComparingTo("10.00");
        assertThatThrownBy(() -> PriceType.RETAIL.normalize(BigDecimal.ZERO))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> PriceType.RETAIL.normalize(null)).isInstanceOf(BizException.class);
    }

    /**
     * 商品状态机只允许「草稿/下架 → 上架」与「上架 → 下架」。
     */
    @Test
    @DisplayName("商品状态机不允许在售回退为草稿")
    void spuStatusTransition() {
        assertThat(SpuStatus.DRAFT.canTransitionTo(SpuStatus.ON_SALE)).isTrue();
        assertThat(SpuStatus.OFF_SALE.canTransitionTo(SpuStatus.ON_SALE)).isTrue();
        assertThat(SpuStatus.ON_SALE.canTransitionTo(SpuStatus.OFF_SALE)).isTrue();
        assertThat(SpuStatus.ON_SALE.canTransitionTo(SpuStatus.DRAFT)).isFalse();
        assertThat(SpuStatus.DRAFT.canTransitionTo(SpuStatus.DRAFT)).isFalse();
        assertThatThrownBy(() -> SpuStatus.ON_SALE.assertTransitionTo(SpuStatus.DRAFT))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.PRODUCT_STATE_CONFLICT));
    }

    /**
     * 商品必须至少有一个 SKU。
     */
    @Test
    @DisplayName("无 SKU 的商品不能保存")
    void spuRequiresSku() {
        Spu spu = new Spu();
        spu.setSpuCode("sp-1");
        spu.setSpuName("测试商品");
        spu.setCategoryId(1L);
        spu.setSkus(new ArrayList<>());
        assertThatThrownBy(spu::validate).isInstanceOf(BizException.class);
    }

    /**
     * 库存不足时断言失败。
     */
    @Test
    @DisplayName("库存守卫：不足即失败")
    void stockGuard() {
        SkuStock stock = SkuStock.empty(1L);
        stock.setAvailableQty(3);
        assertThat(stock.isEnough(3)).isTrue();
        assertThat(stock.isEnough(4)).isFalse();
        assertThatThrownBy(() -> stock.assertEnough(4, "SKU-1"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.STOCK_INSUFFICIENT));
        assertThatThrownBy(() -> SkuStock.validateQuantity(0)).isInstanceOf(BizException.class);
    }

    /**
     * 构造测试 SKU。
     *
     * @param retail 零售价
     * @param b2b 经销价（可为 null）
     * @param points 积分价（可为 null）
     * @return SKU
     */
    private Sku sku(BigDecimal retail, BigDecimal b2b, BigDecimal points) {
        Sku sku = new Sku();
        sku.setSkuCode("SKU-1");
        List<SkuPrice> prices = new ArrayList<>();
        prices.add(price(PriceType.RETAIL, retail));
        if (b2b != null) {
            prices.add(price(PriceType.B2B, b2b));
        }
        if (points != null) {
            prices.add(price(PriceType.POINTS, points));
        }
        sku.setPrices(prices);
        return sku;
    }

    /**
     * 构造价目。
     *
     * @param type 类型
     * @param value 价格
     * @return 价目
     */
    private SkuPrice price(PriceType type, BigDecimal value) {
        SkuPrice price = new SkuPrice();
        price.setPriceType(type);
        price.setPrice(value);
        return price;
    }
}

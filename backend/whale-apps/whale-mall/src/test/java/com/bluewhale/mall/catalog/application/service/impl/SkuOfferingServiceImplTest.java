package com.bluewhale.mall.catalog.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.catalog.application.model.SkuOffering;
import com.bluewhale.mall.catalog.domain.model.PriceType;
import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.model.SkuPrice;
import com.bluewhale.mall.catalog.domain.model.SkuStock;
import com.bluewhale.mall.catalog.domain.model.Spu;
import com.bluewhale.mall.catalog.domain.model.SpuStatus;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.catalog.domain.repository.SpuRepository;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.MallMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * 可售单元解析：四种不可售原因与成交价，以及下单前的最后一道判定。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SkuOfferingServiceImplTest {

    @Mock
    private SpuRepository spuRepository;

    @Mock
    private SkuStockRepository skuStockRepository;

    @InjectMocks
    private SkuOfferingServiceImpl service;

    @Test
    @DisplayName("在售 + 有价 + 有货 → 可售")
    void availableOffering() {
        when(spuRepository.findBySkuIds(anyCollection())).thenReturn(List.of(spu(SpuStatus.ON_SALE, true, true)));
        when(skuStockRepository.findBySkuIds(anyCollection())).thenReturn(List.of(stock(5)));

        SkuOffering offering = service.resolve(List.of(200L), MallMode.RETAIL).get(200L);

        assertThat(offering.isAvailable()).isTrue();
        assertThat(offering.getUnitPrice()).isEqualByComparingTo("19.90");
        assertThat(offering.getPriceType()).isEqualTo("RETAIL");
    }

    @Test
    @DisplayName("未上架商品不可售")
    void offSale() {
        when(spuRepository.findBySkuIds(anyCollection())).thenReturn(List.of(spu(SpuStatus.OFF_SALE, true, true)));
        SkuOffering offering = service.resolve(List.of(200L), MallMode.RETAIL).get(200L);
        assertThat(offering.isAvailable()).isFalse();
        assertThat(offering.getReason()).isEqualTo("商品未上架");
    }

    @Test
    @DisplayName("停用规格不可售")
    void disabledSku() {
        when(spuRepository.findBySkuIds(anyCollection())).thenReturn(List.of(spu(SpuStatus.ON_SALE, false, true)));
        SkuOffering offering = service.resolve(List.of(200L), MallMode.RETAIL).get(200L);
        assertThat(offering.getReason()).isEqualTo("规格已停用");
    }

    @Test
    @DisplayName("该渠道未定价不可售，且原因可读")
    void missingPrice() {
        when(spuRepository.findBySkuIds(anyCollection())).thenReturn(List.of(spu(SpuStatus.ON_SALE, true, false)));
        SkuOffering offering = service.resolve(List.of(200L), MallMode.RETAIL).get(200L);
        assertThat(offering.isAvailable()).isFalse();
        assertThat(offering.getReason()).contains("未配置");
    }

    @Test
    @DisplayName("零库存不可售")
    void outOfStock() {
        when(spuRepository.findBySkuIds(anyCollection())).thenReturn(List.of(spu(SpuStatus.ON_SALE, true, true)));
        when(skuStockRepository.findBySkuIds(anyCollection())).thenReturn(List.of(stock(0)));
        SkuOffering offering = service.resolve(List.of(200L), MallMode.RETAIL).get(200L);
        assertThat(offering.getReason()).isEqualTo("库存不足");
    }

    @Test
    @DisplayName("未查到的 SKU 不出现在结果里")
    void unknownSkuIgnored() {
        when(spuRepository.findBySkuIds(anyCollection())).thenReturn(List.of());
        when(skuStockRepository.findBySkuIds(anyCollection())).thenReturn(List.of());
        Map<Long, SkuOffering> result = service.resolve(List.of(999L), MallMode.RETAIL);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("下单前判定：不可售与库存不足分别报错")
    void requireSellable() {
        SkuOffering offering = new SkuOffering();
        Sku sku = new Sku();
        sku.setSkuCode("SKU-1");
        offering.setSku(sku);
        offering.setAvailable(false);
        offering.setReason("商品未上架");
        assertThatThrownBy(() -> service.requireSellable(offering, 1))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_PRODUCT_UNAVAILABLE));
        assertThatThrownBy(() -> service.requireSellable(null, 1))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.SKU_NOT_FOUND));

        offering.setAvailable(true);
        offering.setUnitPrice(new BigDecimal("19.90"));
        offering.setAvailableQty(2);
        assertThatThrownBy(() -> service.requireSellable(offering, 3))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.STOCK_INSUFFICIENT));
        assertThat(service.requireSellable(offering, 2)).isEqualByComparingTo("19.90");
    }

    private Spu spu(SpuStatus status, boolean enabled, boolean withPrice) {
        Spu spu = new Spu();
        spu.setId(20L);
        spu.setSpuName("测试商品");
        spu.setStatus(status);
        Sku sku = new Sku();
        sku.setId(200L);
        sku.setSpuId(20L);
        sku.setSkuCode("SKU-1");
        sku.setStatus(enabled ? "ENABLED" : "DISABLED");
        if (withPrice) {
            SkuPrice price = new SkuPrice();
            price.setPriceType(PriceType.RETAIL);
            price.setPrice(new BigDecimal("19.90"));
            sku.setPrices(new ArrayList<>(List.of(price)));
        }
        spu.setSkus(new ArrayList<>(List.of(sku)));
        return spu;
    }

    private SkuStock stock(int available) {
        SkuStock stock = SkuStock.empty(200L);
        stock.setAvailableQty(available);
        return stock;
    }
}

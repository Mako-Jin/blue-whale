package com.bluewhale.mall.catalog.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.view.StockView;
import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.model.SkuStock;
import com.bluewhale.mall.catalog.domain.model.Spu;
import com.bluewhale.mall.catalog.domain.model.SpuStatus;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.catalog.domain.repository.SpuRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.config.MallProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存应用服务：编码筛选、视图补全、增量调整。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class StockAppServiceImplTest {

    @Mock
    private SkuStockRepository skuStockRepository;

    @Mock
    private SpuRepository spuRepository;

    @Mock
    private MallProperties properties;

    @InjectMocks
    private StockAppServiceImpl service;

    private MallActor actor;

    @BeforeEach
    void setUp() {
        actor = new MallActor(1L, "admin", Set.of("mall_admin"), Set.of());
        lenient().when(properties.getPageLimit()).thenReturn(100);
    }

    @Test
    @DisplayName("编码无命中直接返回空页，不再查库存")
    void pageWithoutMatch() {
        when(spuRepository.findSkuIdsByCode("NONE", 500)).thenReturn(List.of());

        PageResult<StockView> page = service.page("NONE", 1L, 20L, actor);

        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getTotal()).isZero();
    }

    @Test
    @DisplayName("库存行补齐 SKU 编码与商品名称")
    void pageFillsNames() {
        when(spuRepository.findSkuIdsByCode("SKU", 500)).thenReturn(List.of(200L));
        SkuStock stock = SkuStock.empty(200L);
        stock.setAvailableQty(8);
        stock.setReservedQty(2);
        when(skuStockRepository.page(anyCollection(), anyLong(), anyInt())).thenReturn(List.of(stock));
        when(skuStockRepository.count(anyCollection())).thenReturn(1L);
        Sku sku = new Sku();
        sku.setId(200L);
        sku.setSpuId(20L);
        sku.setSkuCode("SKU-1");
        when(spuRepository.findSkusByIds(anyCollection())).thenReturn(List.of(sku));
        Spu spu = new Spu();
        spu.setId(20L);
        spu.setSpuName("测试商品");
        spu.setStatus(SpuStatus.ON_SALE);
        when(spuRepository.findByIds(anyCollection())).thenReturn(List.of(spu));

        PageResult<StockView> page = service.page("SKU", 1L, 20L, actor);

        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getSpuName()).isEqualTo("测试商品");
        assertThat(page.getRecords().get(0).getTotalQty()).isEqualTo(10);
    }

    @Test
    @DisplayName("无关键字时按页取全部库存")
    void pageWithoutKeyword() {
        when(skuStockRepository.page(anyCollection(), anyLong(), anyInt())).thenReturn(List.of());
        when(skuStockRepository.count(anyCollection())).thenReturn(0L);

        PageResult<StockView> page = service.page(null, 1L, 20L, actor);

        assertThat(page.getTotal()).isZero();
    }

    @Test
    @DisplayName("增量调整委托仓储并返回最新可售量")
    void adjust() {
        when(skuStockRepository.adjust(200L, 5)).thenReturn(15);
        assertThat(service.adjust(200L, 5, actor)).isEqualTo(15);
        verify(skuStockRepository).adjust(200L, 5);
    }
}

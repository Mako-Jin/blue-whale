package com.bluewhale.mall.catalog.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.command.PriceCommand;
import com.bluewhale.mall.catalog.application.command.SkuCommand;
import com.bluewhale.mall.catalog.application.command.SpuSaveCommand;
import com.bluewhale.mall.catalog.application.view.SpuDetailView;
import com.bluewhale.mall.catalog.application.view.SpuView;
import com.bluewhale.mall.catalog.domain.model.Category;
import com.bluewhale.mall.catalog.domain.model.PriceType;
import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.model.SkuPrice;
import com.bluewhale.mall.catalog.domain.model.SkuStock;
import com.bluewhale.mall.catalog.domain.model.Spu;
import com.bluewhale.mall.catalog.domain.model.SpuQuery;
import com.bluewhale.mall.catalog.domain.model.SpuStatus;
import com.bluewhale.mall.catalog.domain.repository.CategoryRepository;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.catalog.domain.repository.SpuRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.config.MallProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品应用服务：编码查重、SKU 归属校验、上架前零售价校验、删除前置条件。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ProductAppServiceImplTest {

    @Mock
    private SpuRepository spuRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SkuStockRepository skuStockRepository;

    @Mock
    private MallProperties properties;

    @InjectMocks
    private ProductAppServiceImpl service;

    private MallActor actor;

    @BeforeEach
    void setUp() {
        actor = new MallActor(1L, "admin", Set.of("mall_admin"), Set.of());
        lenient().when(properties.getPageLimit()).thenReturn(100);
    }

    @Test
    @DisplayName("新建商品落为草稿")
    void createAsDraft() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category()));
        when(spuRepository.existsCode("SP-1", null)).thenReturn(false);
        when(spuRepository.findConflictingSkuCodes(anyCollection(), any())).thenReturn(List.of());
        when(spuRepository.save(any(Spu.class))).thenReturn(88L);

        Long id = service.create(command("SP-1", "SKU-1"), actor);

        assertThat(id).isEqualTo(88L);
        verify(spuRepository).save(any(Spu.class));
    }

    @Test
    @DisplayName("商品编码重复被拒绝")
    void createRejectsDuplicateCode() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category()));
        when(spuRepository.existsCode("SP-1", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(command("SP-1", "SKU-1"), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.PRODUCT_CODE_CONFLICT));
        verify(spuRepository, never()).save(any(Spu.class));
    }

    @Test
    @DisplayName("类目不存在时拒绝建商品")
    void createRejectsMissingCategory() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(command("SP-1", "SKU-1"), actor))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("SKU 编码撞码时给出冲突清单")
    void createRejectsSkuCodeConflict() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category()));
        when(spuRepository.existsCode("SP-1", null)).thenReturn(false);
        when(spuRepository.findConflictingSkuCodes(anyCollection(), any())).thenReturn(List.of("SKU-1"));

        assertThatThrownBy(() -> service.create(command("SP-1", "SKU-1"), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.SKU_CODE_CONFLICT));
    }

    @Test
    @DisplayName("编辑时不能挪走不属于本商品的 SKU")
    void updateRejectsForeignSku() {
        when(spuRepository.findById(5L)).thenReturn(Optional.of(spu(SpuStatus.DRAFT, null)));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category()));
        SpuSaveCommand command = command("SP-1", "SKU-9");
        command.getSkus().get(0).setId(999L);

        assertThatThrownBy(() -> service.update(5L, command, actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.PRODUCT_INVALID));
    }

    @Test
    @DisplayName("缺少零售价不允许上架")
    void publishRequiresRetailPrice() {
        when(spuRepository.findById(5L)).thenReturn(Optional.of(spu(SpuStatus.DRAFT, PriceType.B2B)));

        assertThatThrownBy(() -> service.changeStatus(5L, "ON_SALE", actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.PRICE_MISSING));
        verify(spuRepository, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("有零售价可上架")
    void publishSucceeds() {
        when(spuRepository.findById(5L)).thenReturn(Optional.of(spu(SpuStatus.DRAFT, PriceType.RETAIL)));

        service.changeStatus(5L, "ON_SALE", actor);

        verify(spuRepository).updateStatus(5L, "ON_SALE");
    }

    @Test
    @DisplayName("在售商品不可删除")
    void removeOnSaleRejected() {
        when(spuRepository.findById(5L)).thenReturn(Optional.of(spu(SpuStatus.ON_SALE, PriceType.RETAIL)));

        assertThatThrownBy(() -> service.remove(5L, actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.PRODUCT_STATE_CONFLICT));
        verify(spuRepository, never()).remove(anyLong());
    }

    @Test
    @DisplayName("草稿商品可删除")
    void removeDraftSucceeds() {
        when(spuRepository.findById(5L)).thenReturn(Optional.of(spu(SpuStatus.DRAFT, PriceType.RETAIL)));
        service.remove(5L, actor);
        verify(spuRepository).remove(5L);
    }

    @Test
    @DisplayName("详情带出 SKU、价目与库存")
    void detailIncludesSkuAndStock() {
        when(spuRepository.findById(5L)).thenReturn(Optional.of(spu(SpuStatus.ON_SALE, PriceType.RETAIL)));
        SkuStock stock = SkuStock.empty(200L);
        stock.setAvailableQty(7);
        when(skuStockRepository.findBySkuIds(anyCollection())).thenReturn(List.of(stock));

        SpuDetailView view = service.detail(5L, actor);

        assertThat(view.getSkus()).hasSize(1);
        assertThat(view.getSkus().get(0).getAvailableQty()).isEqualTo(7);
        assertThat(view.getSkus().get(0).getPrices()).hasSize(1);
    }

    @Test
    @DisplayName("列表补齐类目名称")
    void pageFillsCategoryName() {
        when(spuRepository.page(any(SpuQuery.class), anyLong(), anyInt())).thenReturn(List.of(
                spu(SpuStatus.DRAFT, PriceType.RETAIL)));
        when(spuRepository.count(any(SpuQuery.class))).thenReturn(1L);
        when(categoryRepository.list(null)).thenReturn(List.of(category()));

        PageResult<SpuView> page = service.page(new SpuQuery(), actor);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords().get(0).getCategoryName()).isEqualTo("数码");
    }

    /**
     * 构造商品保存命令。
     *
     * @param spuCode 商品编码
     * @param skuCode SKU 编码
     * @return 命令
     */
    private SpuSaveCommand command(String spuCode, String skuCode) {
        SpuSaveCommand command = new SpuSaveCommand();
        command.setSpuCode(spuCode);
        command.setSpuName("测试商品");
        command.setCategoryId(10L);
        SkuCommand sku = new SkuCommand();
        sku.setSkuCode(skuCode);
        PriceCommand price = new PriceCommand();
        price.setPriceType("RETAIL");
        price.setPrice(new BigDecimal("19.90"));
        sku.setPrices(new ArrayList<>(List.of(price)));
        command.setSkus(new ArrayList<>(List.of(sku)));
        return command;
    }

    /**
     * 构造类目。
     *
     * @return 类目
     */
    private Category category() {
        Category category = new Category();
        category.setId(10L);
        category.setCategoryName("数码");
        category.setCategoryLevel(1);
        return category;
    }

    /**
     * 构造商品聚合。
     *
     * @param status 状态
     * @param priceType 价目类型（null 表示无价目）
     * @return 商品
     */
    private Spu spu(SpuStatus status, PriceType priceType) {
        Spu spu = new Spu();
        spu.setId(5L);
        spu.setSpuCode("SP-1");
        spu.setSpuName("测试商品");
        spu.setCategoryId(10L);
        spu.setStatus(status);
        Sku sku = new Sku();
        sku.setId(200L);
        sku.setSpuId(5L);
        sku.setSkuCode("SKU-1");
        sku.setStatus("ENABLED");
        if (priceType != null) {
            SkuPrice price = new SkuPrice();
            price.setPriceType(priceType);
            price.setPrice(new BigDecimal("19.90"));
            sku.setPrices(new ArrayList<>(List.of(price)));
        }
        spu.setSkus(new ArrayList<>(List.of(sku)));
        return spu;
    }
}

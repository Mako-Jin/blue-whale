package com.bluewhale.mall.catalog.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.command.PriceCommand;
import com.bluewhale.mall.catalog.application.command.SkuCommand;
import com.bluewhale.mall.catalog.application.command.SpuSaveCommand;
import com.bluewhale.mall.catalog.application.service.ProductAppService;
import com.bluewhale.mall.catalog.application.view.PriceView;
import com.bluewhale.mall.catalog.application.view.SkuView;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 商品应用服务实现。
 *
 * <p>上架校验只卡一条：<b>每个启用中的 SKU 必须有零售价</b>。零售价是所有渠道的基准，
 * 缺了它零售渠道点进详情就是 {@code PRICE_MISSING}；而经销价与积分价允许后补——
 * 缺经销价有零售价兜底，缺积分价只是福利渠道不上这个商品。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ProductAppServiceImpl implements ProductAppService {

    private final SpuRepository spuRepository;

    private final CategoryRepository categoryRepository;

    private final SkuStockRepository skuStockRepository;

    private final MallProperties properties;

    @Override
    public PageResult<SpuView> page(SpuQuery query, MallActor actor) {
        SpuQuery safe = query == null ? new SpuQuery() : query;
        long size = safe.getSize() == null || safe.getSize() < 1
                ? Math.min(20, properties.getPageLimit()) : Math.min(safe.getSize(), properties.getPageLimit());
        long current = safe.getCurrent() == null || safe.getCurrent() < 1 ? 1L : safe.getCurrent();
        List<Spu> spus = spuRepository.page(safe, (current - 1L) * size, (int) size);
        Map<Long, String> categoryNames = categoryNameIndex();
        List<SpuView> views = new ArrayList<>(spus.size());
        for (Spu spu : spus) {
            SpuView view = new SpuView();
            fillBase(view, spu, categoryNames.get(spu.getCategoryId()));
            views.add(view);
        }
        return PageResult.of(views, spuRepository.count(safe), current, size);
    }

    @Override
    public SpuDetailView detail(Long id, MallActor actor) {
        Spu spu = requireProduct(id);
        Map<Long, SkuStock> stocks = stockIndex(spu);
        SpuDetailView view = new SpuDetailView();
        fillBase(view, spu, categoryNameIndex().get(spu.getCategoryId()));
        view.setImages(spu.getImages());
        view.setDetail(spu.getDetail());
        view.setRemark(spu.getRemark());
        for (Sku sku : spu.getSkus()) {
            view.getSkus().add(toSkuView(sku, stocks.get(sku.getId())));
        }
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SpuSaveCommand command, MallActor actor) {
        actor.requireUserId();
        assertCategoryExists(command.getCategoryId());
        Spu spu = new Spu();
        applyCommand(spu, command);
        spu.setStatus(SpuStatus.DRAFT);
        spu.validate();
        if (spuRepository.existsCode(spu.getSpuCode(), null)) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_CODE_CONFLICT);
        }
        assertSkuCodesFree(spu, null);
        return spuRepository.save(spu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SpuSaveCommand command, MallActor actor) {
        actor.requireUserId();
        Spu existing = requireProduct(id);
        assertCategoryExists(command.getCategoryId());
        assertSkuOwnership(existing, command);
        Spu spu = new Spu();
        spu.setId(existing.getId());
        spu.setStatus(existing.getStatus());
        applyCommand(spu, command);
        spu.validate();
        if (spuRepository.existsCode(spu.getSpuCode(), id)) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_CODE_CONFLICT);
        }
        assertSkuCodesFree(spu, id);
        spuRepository.save(spu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String target, MallActor actor) {
        actor.requireUserId();
        Spu spu = requireProduct(id);
        SpuStatus status = SpuStatus.parse(target);
        if (status == SpuStatus.ON_SALE) {
            assertPublishable(spu);
            spu.publish();
        } else if (status == SpuStatus.OFF_SALE) {
            spu.offShelf();
        } else {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_STATE_CONFLICT,
                    "只支持切换到 ON_SALE 或 OFF_SALE");
        }
        spuRepository.updateStatus(id, spu.getStatus().name());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id, MallActor actor) {
        actor.requireUserId();
        Spu spu = requireProduct(id);
        if (!spu.canDelete()) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_STATE_CONFLICT,
                    "在售商品不可删除，请先下架");
        }
        spuRepository.remove(id);
    }

    /**
     * 上架前校验：每个启用 SKU 必须有零售价。
     *
     * @param spu 商品
     */
    private void assertPublishable(Spu spu) {
        for (Sku sku : spu.getSkus()) {
            if (sku.isEnabled() && sku.findPrice(PriceType.RETAIL) == null) {
                throw MallErrorCode.exception(MallErrorCode.PRICE_MISSING,
                        "上架前请为 SKU " + sku.getSkuCode() + " 配置零售价");
            }
        }
    }

    /**
     * 校验提交的 SKU ID 都属于该商品（防止通过改 id 挪走别人的 SKU）。
     *
     * @param existing 现有商品
     * @param command 命令
     */
    private void assertSkuOwnership(Spu existing, SpuSaveCommand command) {
        for (SkuCommand sku : command.getSkus()) {
            if (sku.getId() != null && !existing.containsSku(sku.getId())) {
                throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID,
                        "SKU " + sku.getSkuCode() + " 不属于该商品");
            }
        }
    }

    /**
     * 校验 SKU 编码未被其他商品占用。
     *
     * @param spu 商品
     * @param excludeSpuId 需排除的商品 ID
     */
    private void assertSkuCodesFree(Spu spu, Long excludeSpuId) {
        List<String> codes = spu.getSkus().stream().map(Sku::getSkuCode).toList();
        List<String> conflicts = spuRepository.findConflictingSkuCodes(codes, excludeSpuId);
        if (!conflicts.isEmpty()) {
            throw MallErrorCode.exception(MallErrorCode.SKU_CODE_CONFLICT,
                    "SKU 编码已被占用：" + String.join("、", conflicts));
        }
        Set<String> unique = new LinkedHashSet<>(codes);
        if (unique.size() != codes.size()) {
            throw MallErrorCode.exception(MallErrorCode.SKU_CODE_CONFLICT, "同一商品内 SKU 编码重复");
        }
    }

    /**
     * 校验类目存在。
     *
     * @param categoryId 类目 ID
     */
    private void assertCategoryExists(Long categoryId) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.CATEGORY_NOT_FOUND));
    }

    /**
     * 查询商品，不存在即失败。
     *
     * @param id 商品 ID
     * @return 商品聚合
     */
    private Spu requireProduct(Long id) {
        return spuRepository.findById(id)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.PRODUCT_NOT_FOUND));
    }

    /**
     * 将命令写入商品聚合。
     *
     * @param spu 商品聚合
     * @param command 命令
     */
    private void applyCommand(Spu spu, SpuSaveCommand command) {
        spu.setSpuCode(command.getSpuCode());
        spu.setSpuName(command.getSpuName());
        spu.setCategoryId(command.getCategoryId());
        spu.setBrand(command.getBrand());
        spu.setMainImage(command.getMainImage());
        spu.setImages(command.getImages() == null ? new ArrayList<>() : new ArrayList<>(command.getImages()));
        spu.setDetail(command.getDetail());
        spu.setSortNo(command.getSortNo() == null ? 0 : command.getSortNo());
        spu.setRemark(command.getRemark());
        List<Sku> skus = new ArrayList<>(command.getSkus().size());
        for (SkuCommand skuCommand : command.getSkus()) {
            skus.add(toSku(skuCommand));
        }
        spu.setSkus(skus);
    }

    /**
     * 命令转 SKU。
     *
     * @param command SKU 命令
     * @return SKU
     */
    private Sku toSku(SkuCommand command) {
        Sku sku = new Sku();
        sku.setId(command.getId());
        sku.setSkuCode(command.getSkuCode());
        sku.setSpecJson(command.getSpecJson());
        sku.setImageUrl(command.getImageUrl());
        sku.setStatus(command.getStatus() == null ? "ENABLED" : command.getStatus().toUpperCase(Locale.ROOT));
        sku.setSortNo(command.getSortNo() == null ? 0 : command.getSortNo());
        sku.setWeightKg(command.getWeightKg());
        List<SkuPrice> prices = new ArrayList<>(command.getPrices().size());
        for (PriceCommand priceCommand : command.getPrices()) {
            PriceType priceType = PriceType.parse(priceCommand.getPriceType());
            if (priceType == null) {
                throw MallErrorCode.exception(MallErrorCode.PRICE_INVALID,
                        "未知价目类型：" + priceCommand.getPriceType());
            }
            SkuPrice price = new SkuPrice();
            price.setPriceType(priceType);
            price.setPrice(priceCommand.getPrice());
            prices.add(price);
        }
        sku.setPrices(prices);
        return sku;
    }

    /**
     * 填充商品基础字段。
     *
     * @param view 视图
     * @param spu 商品
     * @param categoryName 类目名称
     */
    private void fillBase(SpuView view, Spu spu, String categoryName) {
        view.setId(spu.getId());
        view.setSpuCode(spu.getSpuCode());
        view.setSpuName(spu.getSpuName());
        view.setCategoryId(spu.getCategoryId());
        view.setCategoryName(categoryName);
        view.setBrand(spu.getBrand());
        view.setMainImage(spu.getMainImage());
        view.setStatus(spu.getStatus() == null ? SpuStatus.DRAFT.name() : spu.getStatus().name());
        view.setSortNo(spu.getSortNo());
        view.setUpdateTime(spu.getUpdateTime());
    }

    /**
     * 组装 SKU 视图。
     *
     * @param sku SKU
     * @param stock 库存（可为 null）
     * @return SKU 视图
     */
    private SkuView toSkuView(Sku sku, SkuStock stock) {
        SkuView view = new SkuView();
        view.setId(sku.getId());
        view.setSkuCode(sku.getSkuCode());
        view.setSpecJson(sku.getSpecJson());
        view.setImageUrl(sku.getImageUrl());
        view.setStatus(sku.isEnabled() ? "ENABLED" : "DISABLED");
        view.setSortNo(sku.getSortNo());
        view.setWeightKg(sku.getWeightKg());
        view.setAvailableQty(stock == null ? 0 : stock.getAvailableQty());
        view.setReservedQty(stock == null ? 0 : stock.getReservedQty());
        for (SkuPrice price : sku.getPrices()) {
            PriceView priceView = new PriceView();
            priceView.setPriceType(price.getPriceType() == null ? null : price.getPriceType().name());
            priceView.setPrice(price.getPrice());
            view.getPrices().add(priceView);
        }
        return view;
    }

    /**
     * 类目名称索引。
     *
     * @return 类目 ID → 名称
     */
    private Map<Long, String> categoryNameIndex() {
        Map<Long, String> index = new HashMap<>();
        for (Category category : categoryRepository.list(null)) {
            index.put(category.getId(), category.getCategoryName());
        }
        return index;
    }

    /**
     * 库存索引。
     *
     * @param spu 商品
     * @return SKU ID → 库存
     */
    private Map<Long, SkuStock> stockIndex(Spu spu) {
        List<Long> skuIds = spu.getSkus().stream().map(Sku::getId).filter(java.util.Objects::nonNull).toList();
        Map<Long, SkuStock> index = new HashMap<>();
        for (SkuStock stock : skuStockRepository.findBySkuIds(skuIds)) {
            index.put(stock.getSkuId(), stock);
        }
        return index;
    }
}

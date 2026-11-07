package com.bluewhale.mall.catalog.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.service.StockAppService;
import com.bluewhale.mall.catalog.application.view.StockView;
import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.model.SkuStock;
import com.bluewhale.mall.catalog.domain.model.Spu;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.catalog.domain.repository.SpuRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存应用服务实现。
 *
 * <p>按 SKU 编码筛选时先取命中编码的 SKU ID（上限 500）再分页查库存：
 * 直接在库存表上做 LIKE 筛不到编码（编码在 SKU 表），而联表分页会把这个
 * 纯粹的后台列表查询变成一条难以优化的复杂 SQL。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class StockAppServiceImpl implements StockAppService {

    /** 按编码筛选时最多回查的 SKU 数 */
    private static final int SKU_SEARCH_LIMIT = 500;

    private final SkuStockRepository skuStockRepository;

    private final SpuRepository spuRepository;

    private final MallProperties properties;

    @Override
    public PageResult<StockView> page(String keyword, Long current, Long size, MallActor actor) {
        long pageSize = size == null || size < 1
                ? Math.min(20, properties.getPageLimit()) : Math.min(size, properties.getPageLimit());
        long pageNo = current == null || current < 1 ? 1L : current;
        List<Long> skuIds = List.of();
        if (StringUtils.hasText(keyword)) {
            skuIds = spuRepository.findSkuIdsByCode(keyword, SKU_SEARCH_LIMIT);
            if (skuIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, pageNo, pageSize);
            }
        }
        List<SkuStock> stocks = skuStockRepository.page(skuIds, (pageNo - 1L) * pageSize, (int) pageSize);
        long total = skuStockRepository.count(skuIds);
        return PageResult.of(toViews(stocks), total, pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int adjust(Long skuId, int delta, MallActor actor) {
        actor.requireUserId();
        if (skuId == null || skuId <= 0) {
            throw MallErrorCode.exception(MallErrorCode.STOCK_NOT_FOUND);
        }
        return skuStockRepository.adjust(skuId, delta);
    }

    /**
     * 组装库存视图（补齐 SKU 编码与商品名称）。
     *
     * @param stocks 库存列表
     * @return 视图列表
     */
    private List<StockView> toViews(List<SkuStock> stocks) {
        if (stocks.isEmpty()) {
            return List.of();
        }
        List<Long> skuIds = stocks.stream().map(SkuStock::getSkuId).toList();
        Map<Long, Sku> skuIndex = new HashMap<>();
        Map<Long, Long> spuIdBySku = new HashMap<>();
        for (Sku sku : spuRepository.findSkusByIds(skuIds)) {
            skuIndex.put(sku.getId(), sku);
            spuIdBySku.put(sku.getId(), sku.getSpuId());
        }
        Map<Long, Spu> spuIndex = new HashMap<>();
        for (Spu spu : spuRepository.findByIds(spuIdBySku.values())) {
            spuIndex.put(spu.getId(), spu);
        }
        List<StockView> views = new ArrayList<>(stocks.size());
        for (SkuStock stock : stocks) {
            Sku sku = skuIndex.get(stock.getSkuId());
            Spu spu = sku == null ? null : spuIndex.get(sku.getSpuId());
            StockView view = new StockView();
            view.setSkuId(stock.getSkuId());
            view.setSkuCode(sku == null ? null : sku.getSkuCode());
            view.setSpecJson(sku == null ? null : sku.getSpecJson());
            view.setSpuId(spu == null ? null : spu.getId());
            view.setSpuName(spu == null ? null : spu.getSpuName());
            view.setSpuStatus(spu == null || spu.getStatus() == null ? null : spu.getStatus().name());
            view.setAvailableQty(stock.getAvailableQty());
            view.setReservedQty(stock.getReservedQty());
            view.setTotalQty(stock.totalQty());
            view.setUpdateTime(stock.getUpdateTime());
            views.add(view);
        }
        return views;
    }
}

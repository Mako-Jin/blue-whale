package com.bluewhale.mall.catalog.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.command.StockInboundCommand;
import com.bluewhale.mall.catalog.application.service.StockAppService;
import com.bluewhale.mall.catalog.application.view.StockInboundView;
import com.bluewhale.mall.catalog.application.view.StockView;
import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.model.SkuStock;
import com.bluewhale.mall.catalog.domain.model.Spu;
import com.bluewhale.mall.catalog.domain.model.StockInbound;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.catalog.domain.repository.SpuRepository;
import com.bluewhale.mall.catalog.domain.repository.StockInboundRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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

    private final StockInboundRepository stockInboundRepository;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockInboundView inbound(StockInboundCommand command) {
        StockInbound inbound = command == null ? null : command.toDomain();
        if (inbound == null) {
            throw MallErrorCode.exception(MallErrorCode.INBOUND_INVALID, "入库命令不能为空");
        }
        inbound.validate();
        // 幂等：同单号重复推送直接返回首次结果，不再加库存
        StockInbound existing = stockInboundRepository.findByDocNo(inbound.getDocNo());
        if (existing != null) {
            return toInboundView(existing, true);
        }
        for (StockInbound.StockInboundLine line : inbound.getLines()) {
            line.setSkuId(requireSkuId(line));
            // 入库可能先于商品首次调库存：行不存在时先初始化再增量
            skuStockRepository.init(line.getSkuId());
            skuStockRepository.adjust(line.getSkuId(), line.getQuantity());
        }
        try {
            stockInboundRepository.save(inbound);
        } catch (DuplicateKeyException e) {
            // 并发双推同单号：唯一键兜底，按幂等返回已入库的结果
            StockInbound winner = stockInboundRepository.findByDocNo(inbound.getDocNo());
            if (winner == null) {
                throw e;
            }
            return toInboundView(winner, true);
        }
        return toInboundView(inbound, false);
    }

    /**
     * 解析行 SKU ID：直接给 ID 用 ID；只给编码时精确匹配（模糊命中多条则拒绝，
     * 宁可退单重推也不写错库存）。
     *
     * @param line 入库行
     * @return SKU ID
     */
    private Long requireSkuId(StockInbound.StockInboundLine line) {
        if (line.getSkuId() != null && line.getSkuId() > 0) {
            return line.getSkuId();
        }
        if (line.getSkuCode() == null || line.getSkuCode().isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.INBOUND_INVALID, "入库明细必须提供 SKU ID 或 SKU 编码");
        }
        List<Long> candidates = spuRepository.findSkuIdsByCode(line.getSkuCode(), 50);
        if (candidates.isEmpty()) {
            throw MallErrorCode.exception(MallErrorCode.SKU_NOT_FOUND,
                    "入库明细 SKU 编码不存在：" + line.getSkuCode());
        }
        Map<Long, Sku> skuIndex = new HashMap<>();
        for (Sku sku : spuRepository.findSkusByIds(candidates)) {
            skuIndex.put(sku.getId(), sku);
        }
        List<Long> exact = candidates.stream()
                .filter(id -> line.getSkuCode().equalsIgnoreCase(
                        skuIndex.get(id) == null ? "" : skuIndex.get(id).getSkuCode()))
                .toList();
        if (exact.size() != 1) {
            throw MallErrorCode.exception(MallErrorCode.INBOUND_INVALID,
                    "入库明细 SKU 编码无法唯一定位：" + line.getSkuCode());
        }
        return exact.get(0);
    }

    /**
     * 组装入库结果视图。
     *
     * @param inbound 入库单
     * @param duplicated 是否幂等命中
     * @return 视图
     */
    private StockInboundView toInboundView(StockInbound inbound, boolean duplicated) {
        StockInboundView view = new StockInboundView();
        view.setDocNo(inbound.getDocNo());
        view.setSource(inbound.getSource());
        view.setDuplicated(duplicated);
        view.setTotalQuantity(inbound.getLines().stream()
                .mapToInt(StockInbound.StockInboundLine::getQuantity).sum());
        view.setCreateTime(inbound.getCreateTime());
        return view;
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

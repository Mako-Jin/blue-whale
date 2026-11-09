package com.bluewhale.mall.catalog.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.catalog.application.model.SkuOffering;
import com.bluewhale.mall.catalog.application.service.SkuOfferingService;
import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.model.PriceType;
import com.bluewhale.mall.catalog.domain.model.SkuPrice;
import com.bluewhale.mall.catalog.domain.model.SkuStock;
import com.bluewhale.mall.catalog.domain.model.Spu;
import com.bluewhale.mall.catalog.domain.model.SpuStatus;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.catalog.domain.repository.SpuRepository;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.MallMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 可售单元解析实现。
 *
 * <p>四种不可购买原因分开给出，是为了让前端能说人话：
 * 「商品已下架」「规格已停用」「该渠道未定价」「库存不足」对用户是完全不同的处置
 * （等补货 / 换规格 / 换个渠道买 / 少买点）。统一成一句「不可购买」等于没说。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SkuOfferingServiceImpl implements SkuOfferingService {

    private final SpuRepository spuRepository;

    private final SkuStockRepository skuStockRepository;

    @Override
    public Map<Long, SkuOffering> resolve(Collection<Long> skuIds, MallMode mode) {
        return resolve(skuIds, mode, false);
    }

    @Override
    public Map<Long, SkuOffering> resolve(Collection<Long> skuIds, MallMode mode, boolean memberPrice) {
        Map<Long, SkuOffering> result = new LinkedHashMap<>();
        if (skuIds == null || skuIds.isEmpty()) {
            return result;
        }
        Map<Long, SkuStock> stocks = new HashMap<>();
        for (SkuStock stock : skuStockRepository.findBySkuIds(skuIds)) {
            stocks.put(stock.getSkuId(), stock);
        }
        for (Spu spu : spuRepository.findBySkuIds(skuIds)) {
            for (Sku sku : spu.getSkus()) {
                if (sku.getId() == null || !skuIds.contains(sku.getId())) {
                    continue;
                }
                result.put(sku.getId(), build(spu, sku, stocks.get(sku.getId()), mode, memberPrice));
            }
        }
        return result;
    }

    @Override
    public List<SkuOffering> resolveList(Collection<Long> skuIds, MallMode mode) {
        return resolveList(skuIds, mode, false);
    }

    @Override
    public List<SkuOffering> resolveList(Collection<Long> skuIds, MallMode mode, boolean memberPrice) {
        Map<Long, SkuOffering> resolved = resolve(new LinkedHashSet<>(skuIds == null ? List.of() : skuIds),
                mode, memberPrice);
        List<SkuOffering> list = new ArrayList<>(resolved.size());
        if (skuIds == null) {
            return list;
        }
        for (Long skuId : skuIds) {
            SkuOffering offering = resolved.get(skuId);
            if (offering != null) {
                list.add(offering);
            }
        }
        return list;
    }

    /**
     * 组装单个可售单元（含会员价口径）。
     *
     * <p>会员价命中时 {@code unitPrice} 为会员价、{@code listPrice} 为会员价
     * 覆盖前的普通价目（订单行原价快照用）；普通价目缺失时原价快照回落为空，
     * 让订单按成交价补齐口径，而不是让会员买不了东西。</p>
     *
     * @param spu 商品
     * @param sku 规格
     * @param stock 库存，可为 null
     * @param mode 商城形态
     * @param memberPrice 下单用户是否为有效会员
     * @return 可售单元
     */
    private SkuOffering build(Spu spu, Sku sku, SkuStock stock, MallMode mode, boolean memberPrice) {
        SkuOffering offering = new SkuOffering();
        offering.setSku(sku);
        offering.setSpuId(spu.getId());
        offering.setSpuName(spu.getSpuName());
        offering.setCategoryId(spu.getCategoryId());
        offering.setMainImage(spu.getMainImage());
        offering.setSpuStatus(spu.getStatus());
        offering.setAvailableQty(stock == null ? 0 : stock.getAvailableQty());
        offering.setAvailable(false);
        if (spu.getStatus() != SpuStatus.ON_SALE) {
            offering.setReason("商品未上架");
            return offering;
        }
        if (!sku.isEnabled()) {
            offering.setReason("规格已停用");
            return offering;
        }
        SkuPrice price;
        try {
            price = sku.resolvePrice(mode, memberPrice);
        } catch (BizException exception) {
            offering.setReason(exception.getMessage());
            return offering;
        }
        if (price.getPriceType() == PriceType.MEMBER) {
            try {
                offering.setListPrice(sku.resolvePrice(mode).getPrice());
            } catch (BizException exception) {
                offering.setListPrice(null);
            }
        }
        offering.setPriceType(price.getPriceType().name());
        offering.setUnitPrice(price.getPrice());
        if (offering.getAvailableQty() <= 0) {
            offering.setReason("库存不足");
            return offering;
        }
        offering.setAvailable(true);
        offering.setReason(null);
        return offering;
    }

    /**
     * 校验可售并返回成交价（下单前最后一道判定）。
     *
     * @param offering 可售单元
     * @param quantity 购买数量
     * @return 成交单价
     */
    public java.math.BigDecimal requireSellable(SkuOffering offering, int quantity) {
        if (offering == null) {
            throw MallErrorCode.exception(MallErrorCode.SKU_NOT_FOUND);
        }
        if (!offering.isAvailable()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_PRODUCT_UNAVAILABLE,
                    "SKU " + offering.getSku().getSkuCode() + " 当前不可购买：" + offering.getReason());
        }
        if (offering.getAvailableQty() == null || offering.getAvailableQty() < quantity) {
            throw MallErrorCode.exception(MallErrorCode.STOCK_INSUFFICIENT,
                    "SKU " + offering.getSku().getSkuCode() + " 库存不足");
        }
        return offering.getUnitPrice();
    }
}

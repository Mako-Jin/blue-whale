package com.bluewhale.mall.catalog.application.service;

import com.bluewhale.mall.catalog.application.model.SkuOffering;
import com.bluewhale.mall.common.enums.MallMode;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 可售单元解析服务（购物车与下单共用的判定入口）。
 *
 * @author BlueWhale
 */
public interface SkuOfferingService {

    /**
     * 批量解析 SKU 在某商城形态下的可售状态与成交价。
     *
     * @param skuIds SKU ID 集合
     * @param mode 商城形态
     * @return SKU ID → 可售单元（未查到的 SKU 不出现在结果中）
     */
    Map<Long, SkuOffering> resolve(Collection<Long> skuIds, MallMode mode);

    /**
     * 批量解析（含会员价口径）。
     *
     * @param skuIds SKU ID 集合
     * @param mode 商城形态
     * @param memberPrice 下单用户是否为有效会员（零售形态生效）
     * @return SKU ID → 可售单元（未查到的 SKU 不出现在结果中）
     */
    Map<Long, SkuOffering> resolve(Collection<Long> skuIds, MallMode mode, boolean memberPrice);

    /**
     * 批量解析并返回列表（保持入参顺序）。
     *
     * @param skuIds SKU ID 集合
     * @param mode 商城形态
     * @return 可售单元列表
     */
    List<SkuOffering> resolveList(Collection<Long> skuIds, MallMode mode);

    /**
     * 批量解析并返回列表（含会员价口径，保持入参顺序）。
     *
     * @param skuIds SKU ID 集合
     * @param mode 商城形态
     * @param memberPrice 下单用户是否为有效会员（零售形态生效）
     * @return 可售单元列表
     */
    List<SkuOffering> resolveList(Collection<Long> skuIds, MallMode mode, boolean memberPrice);

    /**
     * 校验可售并返回成交价（下单前的最后一道判定）。
     *
     * @param offering 可售单元
     * @param quantity 购买数量
     * @return 成交单价
     */
    java.math.BigDecimal requireSellable(SkuOffering offering, int quantity);
}

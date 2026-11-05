package com.bluewhale.erp.scm.application.service;

import com.bluewhale.erp.scm.application.view.PriceTrendView;
import com.bluewhale.erp.scm.application.view.SupplierPriceCompareView;

import java.time.LocalDate;
import java.util.List;

/**
 * 采购比价与价格趋势应用服务。
 *
 * @author BlueWhale
 */
public interface PriceCompareAppService {

    /**
     * 供应商比价：当前有效采购价与最近成交价对比，标记最低价。
     *
     * @param itemId 物料主键
     * @param date 取价日期（空表示今天）
     * @return 比价列表（按当前价升序，未配置价目的排在末尾）
     */
    List<SupplierPriceCompareView> compare(Long itemId, LocalDate date);

    /**
     * 物料采购价格趋势：按月汇总有效采购订单的加权均价、最低价、最高价与笔数。
     *
     * @param itemId 物料主键
     * @param months 统计月数（默认 6，范围 1–24）
     * @return 趋势列表（按月份升序）
     */
    List<PriceTrendView> trend(Long itemId, Integer months);
}
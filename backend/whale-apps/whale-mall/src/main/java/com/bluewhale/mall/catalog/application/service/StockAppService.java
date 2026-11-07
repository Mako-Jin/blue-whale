package com.bluewhale.mall.catalog.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.view.StockView;
import com.bluewhale.mall.common.MallActor;

/**
 * 库存应用服务。
 *
 * @author BlueWhale
 */
public interface StockAppService {

    /**
     * 库存分页（按 SKU 编码筛选）。
     *
     * @param keyword SKU 编码关键字
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<StockView> page(String keyword, Long current, Long size, MallActor actor);

    /**
     * 调整可售库存（增量）。
     *
     * @param skuId SKU ID
     * @param delta 增量，可为负
     * @param actor 操作人
     * @return 调整后的可售数量
     */
    int adjust(Long skuId, int delta, MallActor actor);
}

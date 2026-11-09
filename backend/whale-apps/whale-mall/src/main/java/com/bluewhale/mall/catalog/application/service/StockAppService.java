package com.bluewhale.mall.catalog.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.command.StockInboundCommand;
import com.bluewhale.mall.catalog.application.view.StockInboundView;
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

    /**
     * WMS 入库联动（docNo 幂等）。
     *
     * <p>把 WMS/ERP 推送的入库数量加回商城可售库存：重复推送同一单号
     * 直接返回首次入库结果（duplicated = true），绝不二次加库存。</p>
     *
     * @param command 入库命令
     * @return 入库结果
     */
    StockInboundView inbound(StockInboundCommand command);
}

package com.bluewhale.mall.catalog.domain.repository;

import com.bluewhale.mall.catalog.domain.model.StockInbound;

/**
 * 库存入库单仓储（WMS 联动，docNo 幂等）。
 *
 * @author BlueWhale
 */
public interface StockInboundRepository {

    /**
     * 按单号查询（幂等判定用）。
     *
     * @param docNo 入库单号
     * @return 入库单；不存在返回空
     */
    StockInbound findByDocNo(String docNo);

    /**
     * 保存入库单（头 + 明细），返回头主键。
     *
     * @param inbound 入库单
     * @return 入库单主键
     */
    Long save(StockInbound inbound);
}

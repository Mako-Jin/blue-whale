package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.erp.mfg.application.service.MfgBoardAppService;
import com.bluewhale.erp.mfg.application.view.MfgBoardView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.ProductionOrder;
import com.bluewhale.erp.mfg.domain.repository.ProductionOrderRepository;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 制造看板聚合服务实现。
 *
 * <p>只读：直接复用生产订单与库存余额的既有查询，不另建缓存与物化表，
 * 保证看板数量与业务表逐字段一致。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MfgBoardAppServiceImpl implements MfgBoardAppService {

    /** 默认每类记录上限 */
    private static final int DEFAULT_LIMIT = 50;

    /** 每类记录上限（与分页上限一致） */
    private static final int MAX_LIMIT = 200;

    private final ProductionOrderRepository productionOrderRepository;

    private final StockRepository stockRepository;

    @Override
    @Transactional(readOnly = true)
    public MfgBoardView board(Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        MfgQuery orderQuery = new MfgQuery();
        orderQuery.setCurrent(1L);
        orderQuery.setSize(size);
        List<MfgBoardView.OrderLine> orders = productionOrderRepository.page(orderQuery).stream()
                .map(MfgBoardAppServiceImpl::toOrderLine)
                .toList();

        ScmQuery stockQuery = new ScmQuery();
        stockQuery.setCurrent(1L);
        stockQuery.setSize(size);
        List<MfgBoardView.StockLine> stocks = stockRepository.pageBalances(stockQuery).stream()
                .map(MfgBoardAppServiceImpl::toStockLine)
                .toList();

        MfgBoardView view = new MfgBoardView();
        view.setGeneratedAt(LocalDateTime.now());
        view.setOrders(orders);
        view.setStocks(stocks);
        return view;
    }

    /**
     * 生产订单 → 看板行。
     *
     * @param order 生产订单
     * @return 看板行
     */
    private static MfgBoardView.OrderLine toOrderLine(ProductionOrder order) {
        MfgBoardView.OrderLine line = new MfgBoardView.OrderLine();
        line.setOrderNo(order.getOrderNo());
        line.setItemCode(order.getItemCode());
        line.setItemName(order.getItemName());
        line.setPlannedQuantity(order.getPlannedQuantity());
        line.setCompletedQuantity(order.getCompletedQuantity());
        line.setStatus(order.getStatus());
        line.setTargetWarehouseId(order.getTargetWarehouseId());
        line.setTargetWarehouseName(order.getTargetWarehouseName());
        line.setMesWorkOrderCode(order.getMesWorkOrderCode());
        return line;
    }

    /**
     * 库存余额 → 看板行。
     *
     * @param balance 库存余额
     * @return 看板行
     */
    private static MfgBoardView.StockLine toStockLine(StockBalance balance) {
        MfgBoardView.StockLine line = new MfgBoardView.StockLine();
        line.setItemCode(balance.getItemCode());
        line.setItemName(balance.getItemName());
        line.setWarehouseId(balance.getWarehouseId());
        line.setQuantity(balance.getQuantity());
        return line;
    }
}

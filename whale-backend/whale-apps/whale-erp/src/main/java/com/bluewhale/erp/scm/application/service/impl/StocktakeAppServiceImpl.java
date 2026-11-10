package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.StocktakeCreateCommand;
import com.bluewhale.erp.scm.application.service.StocktakeAppService;
import com.bluewhale.erp.scm.application.view.StocktakeView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.model.Stocktake;
import com.bluewhale.erp.scm.domain.model.StocktakeLine;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import com.bluewhale.erp.scm.domain.repository.StocktakeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存盘点应用服务实现。
 *
 * <p>建档时按当前余额快照账面数量与单位成本；过账按差异生成出入库流水，
 * 盘盈记「借存货、贷待处理财产损溢」，盘亏记反向，净差异按盘点单号幂等生成凭证。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class StocktakeAppServiceImpl implements StocktakeAppService {

    private final StocktakeRepository stocktakeRepository;

    private final StockRepository stockRepository;

    private final ItemCatalog itemCatalog;

    private final WarehouseRepository warehouseRepository;

    private final AutoVoucherAppService autoVoucherAppService;

    @Override
    public PageResult<StocktakeView> page(ScmQuery query) {
        List<StocktakeView> records = stocktakeRepository.page(query).stream().map(StocktakeView::of).toList();
        return PageResult.of(records, stocktakeRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public StocktakeView detail(Long id) {
        return StocktakeView.of(requireStocktake(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeView create(StocktakeCreateCommand command) {
        Warehouse warehouse = requireWarehouse(command.getWarehouseId());
        List<StocktakeLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (StocktakeCreateCommand.Line source : command.getLines()) {
            Quantities.nonNegative(source.getCountedQuantity(), "实盘数量");
            ItemSnapshot item = itemCatalog.findByCode(source.getItemCode())
                    .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID,
                            "物料 " + source.getItemCode() + " 不存在"));
            if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
                throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + item.getCode() + " 已停用");
            }
            StockBalance balance = stockRepository.findBalance(item.getId(), warehouse.getId()).orElse(null);
            StocktakeLine line = new StocktakeLine();
            line.setLineNo(lineNo++);
            line.setItemId(item.getId());
            line.setItemCode(item.getCode());
            line.setItemName(item.getName());
            line.setUom(source.getUom());
            line.setBookQuantity(balance == null ? BigDecimal.ZERO : balance.getQuantity());
            line.setCountedQuantity(source.getCountedQuantity());
            line.calculate(balance == null ? BigDecimal.ZERO : averageCost(balance));
            lines.add(line);
        }
        Stocktake stocktake = new Stocktake();
        stocktake.setDocNo(stocktakeRepository.nextDocNo(command.getStocktakeDate()));
        stocktake.setStocktakeDate(command.getStocktakeDate());
        stocktake.setWarehouseId(warehouse.getId());
        stocktake.setWarehouseName(warehouse.getName());
        stocktake.setStatus(ScmStatus.DRAFT);
        stocktake.setRemark(command.getRemark());
        stocktake.setLines(lines);
        stocktake.refreshTotals();
        stocktakeRepository.insert(stocktake);
        return StocktakeView.of(stocktake);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeView post(Long id, Long operatorId) {
        Stocktake stocktake = requireStocktake(id);
        stocktake.post();
        String docNo = stocktake.getDocNo();
        LocalDateTime occurredAt = LocalDateTime.now();
        BigDecimal gainAmount = BigDecimal.ZERO;
        BigDecimal lossAmount = BigDecimal.ZERO;
        for (StocktakeLine line : stocktake.getLines()) {
            BigDecimal difference = line.getDifference() == null ? BigDecimal.ZERO : line.getDifference();
            if (difference.signum() == 0) {
                continue;
            }
            if (difference.signum() > 0) {
                StockBalance balance = stockRepository.findBalance(line.getItemId(), stocktake.getWarehouseId())
                        .orElseGet(() -> newBalance(line, stocktake.getWarehouseId()));
                BigDecimal amount = balance.inbound(difference, line.getUnitCost());
                stockRepository.saveBalance(balance);
                stockRepository.insertMovement(movement(docNo, stocktake, line, ScmStatus.IN, difference,
                        line.getUnitCost(), amount, occurredAt));
                gainAmount = gainAmount.add(amount);
            } else {
                StockBalance balance = stockRepository.findBalance(line.getItemId(), stocktake.getWarehouseId())
                        .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.NEGATIVE_STOCK,
                                "物料 " + line.getItemCode() + " 盘亏数量超过库存"));
                BigDecimal quantity = difference.abs();
                BigDecimal cost = balance.outbound(quantity);
                stockRepository.saveBalance(balance);
                BigDecimal unitCost = cost.divide(quantity, 4, RoundingMode.HALF_UP);
                stockRepository.insertMovement(movement(docNo, stocktake, line, ScmStatus.OUT, quantity,
                        unitCost, cost, occurredAt));
                lossAmount = lossAmount.add(cost);
            }
        }
        stocktake.setGainAmount(gainAmount);
        stocktake.setLossAmount(lossAmount);
        stocktakeRepository.update(stocktake);
        BigDecimal netAmount = gainAmount.subtract(lossAmount);
        if (netAmount.signum() != 0) {
            AutoVoucherCommand command = new AutoVoucherCommand();
            command.setSourceType(ScmStatus.SOURCE_STOCKTAKE);
            command.setSourceId(docNo);
            command.setVoucherDate(stocktake.getStocktakeDate());
            command.setAmount(netAmount);
            command.setOperatorId(operatorId);
            command.setRemark("库存盘点差异：" + docNo);
            autoVoucherAppService.postStockVariance(command);
        }
        return StocktakeView.of(stocktake);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeView cancel(Long id) {
        Stocktake stocktake = requireStocktake(id);
        stocktake.cancel();
        stocktakeRepository.update(stocktake);
        return StocktakeView.of(stocktake);
    }

    /**
     * 查询盘点单，不存在按 404 处理。
     *
     * @param id 主键
     * @return 盘点单
     */
    private Stocktake requireStocktake(Long id) {
        return stocktakeRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.STOCKTAKE_NOT_FOUND));
    }

    /**
     * 校验仓库存在且启用。
     *
     * @param warehouseId 仓库主键
     * @return 仓库
     */
    private Warehouse requireWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.WAREHOUSE_NOT_FOUND));
        if (!MasterDataStatus.ENABLED.equals(warehouse.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.TRANSFER_INVALID, "仓库 " + warehouse.getCode() + " 已停用");
        }
        return warehouse;
    }

    /**
     * 库存移动加权平均单位成本。
     *
     * @param balance 余额
     * @return 单位成本
     */
    private BigDecimal averageCost(StockBalance balance) {
        if (balance.getQuantity() == null || balance.getQuantity().signum() == 0) {
            return BigDecimal.ZERO;
        }
        return balance.getAmount().divide(balance.getQuantity(), 4, RoundingMode.HALF_UP);
    }

    /**
     * 构造盘点流水。
     *
     * @param docNo 盘点单号
     * @param stocktake 盘点单
     * @param line 分录
     * @param direction 方向
     * @param quantity 数量
     * @param unitCost 单位成本
     * @param amount 金额
     * @param occurredAt 发生时间
     * @return 流水
     */
    private StockMovement movement(String docNo, Stocktake stocktake, StocktakeLine line, String direction,
                                   BigDecimal quantity, BigDecimal unitCost, BigDecimal amount,
                                   LocalDateTime occurredAt) {
        StockMovement movement = new StockMovement();
        movement.setDocNo(docNo);
        movement.setSourceType(ScmStatus.SOURCE_STOCKTAKE);
        movement.setSourceId(docNo);
        movement.setItemId(line.getItemId());
        movement.setItemCode(line.getItemCode());
        movement.setItemName(line.getItemName());
        movement.setWarehouseId(stocktake.getWarehouseId());
        movement.setDirection(direction);
        movement.setQuantity(quantity);
        movement.setUnitCost(unitCost);
        movement.setAmount(amount);
        movement.setOccurredAt(occurredAt);
        movement.setRemark("库存盘点：" + docNo);
        return movement;
    }

    /**
     * 构造盘盈新余额。
     *
     * @param line 分录
     * @param warehouseId 仓库主键
     * @return 余额
     */
    private StockBalance newBalance(StocktakeLine line, Long warehouseId) {
        StockBalance balance = new StockBalance();
        balance.setItemId(line.getItemId());
        balance.setItemCode(line.getItemCode());
        balance.setItemName(line.getItemName());
        balance.setWarehouseId(warehouseId);
        balance.setQuantity(BigDecimal.ZERO);
        balance.setAmount(BigDecimal.ZERO);
        return balance;
    }
}

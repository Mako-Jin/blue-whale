package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.StockAdjustCommand;
import com.bluewhale.erp.scm.application.service.InventoryAppService;
import com.bluewhale.erp.scm.application.view.StockBalanceView;
import com.bluewhale.erp.scm.application.view.StockMovementView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import com.bluewhale.erp.vo.ErpInternalStockView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 库存应用服务实现。
 *
 * <p>盘盈按当前移动加权平均成本入库；无结存时按零成本入库，待后续按采购价补价差。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class InventoryAppServiceImpl implements InventoryAppService {

    /** 调整单号前缀 */
    private static final String ADJUST_PREFIX = "ADJ-";

    /** 单据号随机后缀长度 */
    private static final int DOC_SUFFIX_LENGTH = 10;

    /** 单位成本小数位 */
    private static final int COST_SCALE = 4;

    private final StockRepository stockRepository;

    private final ItemCatalog itemCatalog;

    private final WarehouseRepository warehouseRepository;

    @Override
    public PageResult<StockBalanceView> balancePage(ScmQuery query) {
        List<StockBalanceView> records = stockRepository.pageBalances(query).stream()
                .map(StockBalanceView::of).toList();
        return PageResult.of(records, stockRepository.countBalances(query), query.getCurrent(), query.getSize());
    }

    @Override
    public PageResult<StockMovementView> movementPage(ScmQuery query) {
        List<StockMovementView> records = stockRepository.pageMovements(query).stream()
                .map(StockMovementView::of).toList();
        return PageResult.of(records, stockRepository.countMovements(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockMovementView adjust(StockAdjustCommand command, Long operatorId) {
        if (command.getQuantity() == null || command.getQuantity().signum() == 0) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID, "调整数量不能为零");
        }
        ItemSnapshot item = itemCatalog.findByCode(command.getItemCode())
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID,
                        "物料 " + command.getItemCode() + " 不存在"));
        if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + item.getCode() + " 已停用");
        }
        warehouseRepository.findById(command.getWarehouseId())
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.WAREHOUSE_NOT_FOUND));
        StockBalance balance = stockRepository.findBalance(item.getId(), command.getWarehouseId())
                .orElseGet(() -> newBalance(item, command.getWarehouseId()));
        String docNo = ADJUST_PREFIX + UUID.randomUUID().toString().replace("-", "")
                .substring(0, DOC_SUFFIX_LENGTH).toUpperCase();
        BigDecimal quantity = command.getQuantity().abs();
        StockMovement movement = new StockMovement();
        movement.setDocNo(docNo);
        movement.setSourceType(ScmStatus.SOURCE_STOCK_ADJUST);
        movement.setSourceId(docNo);
        movement.setItemId(item.getId());
        movement.setItemCode(item.getCode());
        movement.setItemName(item.getName());
        movement.setWarehouseId(command.getWarehouseId());
        movement.setQuantity(quantity);
        movement.setOccurredAt(LocalDateTime.now());
        movement.setRemark(command.getRemark());
        if (command.getQuantity().signum() > 0) {
            BigDecimal unitCost = averageCost(balance);
            BigDecimal amount = balance.inbound(quantity, unitCost);
            movement.setDirection(ScmStatus.IN);
            movement.setUnitCost(unitCost);
            movement.setAmount(amount);
            stockRepository.saveBalance(balance);
        } else {
            BigDecimal cost = balance.outbound(quantity);
            movement.setDirection(ScmStatus.OUT);
            movement.setUnitCost(quantity.signum() == 0 ? BigDecimal.ZERO
                    : cost.divide(quantity, COST_SCALE, RoundingMode.HALF_UP));
            movement.setAmount(cost);
            stockRepository.saveBalance(balance);
        }
        stockRepository.insertMovement(movement);
        return StockMovementView.of(movement);
    }

    @Override
    public ErpInternalStockView stockByItemCode(String itemCode) {
        ErpInternalStockView view = new ErpInternalStockView();
        view.setItemCode(itemCode);
        view.setTotalQuantity(BigDecimal.ZERO);
        if (!StringUtils.hasText(itemCode)) {
            return view;
        }
        String code = itemCode.trim();
        view.setItemCode(code);
        List<StockBalance> balances = stockRepository.listBalancesByItemCode(code);
        if (balances.isEmpty()) {
            return view;
        }
        view.setItemName(balances.get(0).getItemName());
        BigDecimal total = BigDecimal.ZERO;
        for (StockBalance balance : balances) {
            BigDecimal quantity = balance.getQuantity() == null ? BigDecimal.ZERO : balance.getQuantity();
            total = total.add(quantity);
            ErpInternalStockView.WarehouseStock warehouseStock = new ErpInternalStockView.WarehouseStock();
            warehouseStock.setWarehouseId(balance.getWarehouseId());
            warehouseStock.setQuantity(quantity);
            warehouseRepository.findById(balance.getWarehouseId()).ifPresent(warehouse -> {
                warehouseStock.setWarehouseCode(warehouse.getCode());
                warehouseStock.setWarehouseName(warehouse.getName());
            });
            view.getWarehouses().add(warehouseStock);
        }
        view.setTotalQuantity(total);
        return view;
    }

    /**
     * 当前移动加权平均成本。
     *
     * @param balance 余额
     * @return 单位成本；无结存返回零
     */
    private BigDecimal averageCost(StockBalance balance) {
        BigDecimal quantity = balance.getQuantity() == null ? BigDecimal.ZERO : balance.getQuantity();
        BigDecimal amount = balance.getAmount() == null ? BigDecimal.ZERO : balance.getAmount();
        if (quantity.signum() == 0 || amount.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return amount.divide(quantity, COST_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 构造新余额。
     *
     * @param item 物料快照
     * @param warehouseId 仓库 ID
     * @return 余额
     */
    private StockBalance newBalance(ItemSnapshot item, Long warehouseId) {
        StockBalance balance = new StockBalance();
        balance.setItemId(item.getId());
        balance.setItemCode(item.getCode());
        balance.setItemName(item.getName());
        balance.setWarehouseId(warehouseId);
        balance.setQuantity(BigDecimal.ZERO);
        balance.setAmount(BigDecimal.ZERO);
        return balance;
    }
}

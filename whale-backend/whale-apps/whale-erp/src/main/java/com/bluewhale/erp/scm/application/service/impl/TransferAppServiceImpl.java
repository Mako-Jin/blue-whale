package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.TransferCreateCommand;
import com.bluewhale.erp.scm.application.service.TransferAppService;
import com.bluewhale.erp.scm.application.view.TransferView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.model.Transfer;
import com.bluewhale.erp.scm.domain.model.TransferLine;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import com.bluewhale.erp.scm.domain.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存调拨应用服务实现。
 *
 * <p>过账时调出仓按移动加权平均结转成本，调入仓以同一成本入库；调入仓无余额时新建。
 * 数量不足拒绝负库存，整单同事务回滚。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class TransferAppServiceImpl implements TransferAppService {

    /** 单位成本小数位 */
    private static final int COST_SCALE = 4;

    private final TransferRepository transferRepository;

    private final StockRepository stockRepository;

    private final ItemCatalog itemCatalog;

    private final WarehouseRepository warehouseRepository;

    @Override
    public PageResult<TransferView> page(ScmQuery query) {
        List<TransferView> records = transferRepository.page(query).stream().map(TransferView::of).toList();
        return PageResult.of(records, transferRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public TransferView detail(Long id) {
        return TransferView.of(requireTransfer(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferView create(TransferCreateCommand command) {
        Warehouse from = requireWarehouse(command.getFromWarehouseId());
        Warehouse to = requireWarehouse(command.getToWarehouseId());
        if (from.getId().equals(to.getId())) {
            throw ErpErrorCode.exception(ErpErrorCode.TRANSFER_INVALID, "调出与调入仓库不能相同");
        }
        List<TransferLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (TransferCreateCommand.Line source : command.getLines()) {
            Quantities.requirePositive(source.getQuantity(), "调拨数量");
            ItemSnapshot item = itemCatalog.findByCode(source.getItemCode())
                    .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID,
                            "物料 " + source.getItemCode() + " 不存在"));
            if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
                throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + item.getCode() + " 已停用");
            }
            TransferLine line = new TransferLine();
            line.setLineNo(lineNo++);
            line.setItemId(item.getId());
            line.setItemCode(item.getCode());
            line.setItemName(item.getName());
            line.setUom(source.getUom());
            line.setQuantity(source.getQuantity());
            line.setUnitCost(BigDecimal.ZERO);
            line.setAmount(BigDecimal.ZERO);
            lines.add(line);
        }
        Transfer transfer = new Transfer();
        transfer.setDocNo(transferRepository.nextDocNo(command.getTransferDate()));
        transfer.setTransferDate(command.getTransferDate());
        transfer.setFromWarehouseId(from.getId());
        transfer.setFromWarehouseName(from.getName());
        transfer.setToWarehouseId(to.getId());
        transfer.setToWarehouseName(to.getName());
        transfer.setStatus(ScmStatus.DRAFT);
        transfer.setRemark(command.getRemark());
        transfer.setLines(lines);
        transfer.calculateTotal();
        transferRepository.insert(transfer);
        return TransferView.of(transfer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferView post(Long id) {
        Transfer transfer = requireTransfer(id);
        transfer.post();
        String docNo = transfer.getDocNo();
        LocalDateTime occurredAt = LocalDateTime.now();
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (TransferLine line : transfer.getLines()) {
            StockBalance source = stockRepository.findBalance(line.getItemId(), transfer.getFromWarehouseId())
                    .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.STOCK_NOT_FOUND,
                            "物料 " + line.getItemCode() + " 在调出仓无库存"));
            BigDecimal quantity = line.getQuantity();
            BigDecimal cost = source.outbound(quantity);
            BigDecimal unitCost = cost.divide(quantity, COST_SCALE, RoundingMode.HALF_UP);
            stockRepository.saveBalance(source);
            stockRepository.insertMovement(movement(docNo, transfer, line, transfer.getFromWarehouseId(),
                    ScmStatus.OUT, quantity, unitCost, cost, occurredAt));
            StockBalance target = stockRepository.findBalance(line.getItemId(), transfer.getToWarehouseId())
                    .orElseGet(() -> newBalance(line, transfer.getToWarehouseId()));
            BigDecimal amount = target.inbound(quantity, unitCost);
            stockRepository.saveBalance(target);
            stockRepository.insertMovement(movement(docNo, transfer, line, transfer.getToWarehouseId(),
                    ScmStatus.IN, quantity, unitCost, amount, occurredAt));
            line.setUnitCost(unitCost);
            line.setAmount(amount);
            totalQuantity = totalQuantity.add(quantity);
            totalAmount = totalAmount.add(amount);
        }
        transfer.setTotalQuantity(totalQuantity);
        transfer.setTotalAmount(totalAmount);
        transferRepository.update(transfer);
        return TransferView.of(transfer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferView cancel(Long id) {
        Transfer transfer = requireTransfer(id);
        transfer.cancel();
        transferRepository.update(transfer);
        return TransferView.of(transfer);
    }

    /**
     * 查询调拨单，不存在按 404 处理。
     *
     * @param id 主键
     * @return 调拨单
     */
    private Transfer requireTransfer(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.TRANSFER_NOT_FOUND));
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
     * 构造库存流水。
     *
     * @param docNo 调拨单号
     * @param transfer 调拨单
     * @param line 分录
     * @param warehouseId 仓库主键
     * @param direction 方向
     * @param quantity 数量
     * @param unitCost 单位成本
     * @param amount 金额
     * @param occurredAt 发生时间
     * @return 流水
     */
    private StockMovement movement(String docNo, Transfer transfer, TransferLine line, Long warehouseId,
                                   String direction, BigDecimal quantity, BigDecimal unitCost, BigDecimal amount,
                                   LocalDateTime occurredAt) {
        StockMovement movement = new StockMovement();
        movement.setDocNo(docNo);
        movement.setSourceType(ScmStatus.SOURCE_TRANSFER);
        movement.setSourceId(docNo);
        movement.setItemId(line.getItemId());
        movement.setItemCode(line.getItemCode());
        movement.setItemName(line.getItemName());
        movement.setWarehouseId(warehouseId);
        movement.setDirection(direction);
        movement.setQuantity(quantity);
        movement.setUnitCost(unitCost);
        movement.setAmount(amount);
        movement.setOccurredAt(occurredAt);
        movement.setRemark(transfer.getRemark());
        return movement;
    }

    /**
     * 构造调入仓新余额。
     *
     * @param line 分录
     * @param warehouseId 仓库主键
     * @return 余额
     */
    private StockBalance newBalance(TransferLine line, Long warehouseId) {
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

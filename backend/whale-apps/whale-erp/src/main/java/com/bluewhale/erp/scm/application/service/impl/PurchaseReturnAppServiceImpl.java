package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.scm.application.command.PurchaseReturnCreateCommand;
import com.bluewhale.erp.scm.application.service.PurchaseReturnAppService;
import com.bluewhale.erp.scm.application.view.PurchaseReturnView;
import com.bluewhale.erp.scm.domain.model.PurchaseOrder;
import com.bluewhale.erp.scm.domain.model.PurchaseOrderLine;
import com.bluewhale.erp.scm.domain.model.PurchaseReturn;
import com.bluewhale.erp.scm.domain.model.PurchaseReturnLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.repository.PurchaseOrderRepository;
import com.bluewhale.erp.scm.domain.repository.PurchaseReturnRepository;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购退货应用服务实现。
 *
 * <p>过账闭环：库存出库（按订单单价）→ 应付冲减 → 红字凭证，同一事务内完成。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PurchaseReturnAppServiceImpl implements PurchaseReturnAppService {

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    private final PurchaseReturnRepository purchaseReturnRepository;

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final StockRepository stockRepository;

    private final ArApAppService arApAppService;

    private final AutoVoucherAppService autoVoucherAppService;

    @Override
    public PageResult<PurchaseReturnView> page(ScmQuery query) {
        List<PurchaseReturnView> records = purchaseReturnRepository.page(query).stream()
                .map(PurchaseReturnView::of).toList();
        return PageResult.of(records, purchaseReturnRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public PurchaseReturnView detail(Long id) {
        return PurchaseReturnView.of(requireReturn(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnView create(PurchaseReturnCreateCommand command) {
        PurchaseOrder order = requireOrder(command.getPurchaseOrderId());
        if (!ScmStatus.PARTIALLY_RECEIVED.equals(order.getStatus())
                && !ScmStatus.RECEIVED.equals(order.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.RETURN_INVALID, "采购订单尚未收货，不能退货");
        }
        PurchaseReturn purchaseReturn = new PurchaseReturn();
        purchaseReturn.setPurchaseOrderId(order.getId());
        purchaseReturn.setSupplierId(order.getSupplierId());
        purchaseReturn.setSupplierName(order.getSupplierName());
        purchaseReturn.setDocDate(command.getDocDate());
        purchaseReturn.setWarehouseId(order.getWarehouseId());
        purchaseReturn.setStatus(ScmStatus.DRAFT);
        purchaseReturn.setCurrency(order.getCurrency() == null ? DEFAULT_CURRENCY : order.getCurrency());
        purchaseReturn.setRemark(command.getRemark());
        List<PurchaseReturnLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (PurchaseReturnCreateCommand.Line lineCommand : command.getLines()) {
            PurchaseOrderLine orderLine = order.findLine(lineCommand.getOrderLineNo());
            BigDecimal received = orderLine.getReceivedQuantity() == null
                    ? BigDecimal.ZERO : orderLine.getReceivedQuantity();
            if (received.signum() == 0) {
                throw ErpErrorCode.exception(ErpErrorCode.RETURN_INVALID,
                        "订单第 " + orderLine.getLineNo() + " 行尚未收货");
            }
            BigDecimal already = purchaseReturnRepository.sumReturnedQuantity(order.getId(),
                    orderLine.getLineNo());
            if (already.add(lineCommand.getQuantity()).compareTo(received) > 0) {
                throw ErpErrorCode.exception(ErpErrorCode.RETURN_QUANTITY_EXCEEDED,
                        "订单第 " + orderLine.getLineNo() + " 行可退数量 " + received.subtract(already));
            }
            PurchaseReturnLine line = new PurchaseReturnLine();
            line.setLineNo(lineNo++);
            line.setOrderLineNo(orderLine.getLineNo());
            line.setItemId(orderLine.getItemId());
            line.setItemCode(orderLine.getItemCode());
            line.setItemName(orderLine.getItemName());
            line.setUom(orderLine.getUom());
            line.setQuantity(lineCommand.getQuantity());
            line.setUnitPrice(orderLine.getUnitPrice());
            lines.add(line);
        }
        purchaseReturn.setLines(lines);
        purchaseReturn.calculateTotal();
        purchaseReturn.setDocNo(purchaseReturnRepository.nextDocNo(command.getDocDate()));
        purchaseReturnRepository.insert(purchaseReturn);
        return PurchaseReturnView.of(purchaseReturn);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnView post(Long id, Long operatorId) {
        PurchaseReturn purchaseReturn = requireReturn(id);
        purchaseReturn.post();
        PurchaseOrder order = requireOrder(purchaseReturn.getPurchaseOrderId());
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseReturnLine line : purchaseReturn.getLines()) {
            PurchaseOrderLine orderLine = order.findLine(line.getOrderLineNo());
            BigDecimal received = orderLine.getReceivedQuantity() == null
                    ? BigDecimal.ZERO : orderLine.getReceivedQuantity();
            BigDecimal returned = purchaseReturnRepository.sumReturnedQuantity(purchaseReturn.getPurchaseOrderId(),
                    line.getOrderLineNo());
            if (returned.compareTo(received) > 0) {
                throw ErpErrorCode.exception(ErpErrorCode.RETURN_QUANTITY_EXCEEDED,
                        "订单第 " + line.getOrderLineNo() + " 行累计退货超过已收数量");
            }
            StockBalance balance = stockRepository.findBalance(line.getItemId(), purchaseReturn.getWarehouseId())
                    .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.STOCK_NOT_FOUND,
                            "物料 " + line.getItemCode() + " 在退货仓库无库存"));
            BigDecimal cost = balance.outboundAt(line.getQuantity(), line.getUnitPrice());
            stockRepository.saveBalance(balance);
            line.setUnitCost(line.getUnitPrice());
            line.setCostAmount(cost);
            total = total.add(cost);
            stockRepository.insertMovement(movement(purchaseReturn.getDocNo(), line, purchaseReturn.getWarehouseId(),
                    cost, operatorId));
        }
        purchaseReturn.setTotalAmount(total);
        arApAppService.offsetPayable(purchaseReturn.getSupplierId(), total);
        AutoVoucherCommand voucherCommand = new AutoVoucherCommand();
        voucherCommand.setSourceType(ScmStatus.SOURCE_PURCHASE_RETURN);
        voucherCommand.setSourceId(purchaseReturn.getDocNo());
        voucherCommand.setVoucherDate(purchaseReturn.getDocDate());
        voucherCommand.setCurrency(purchaseReturn.getCurrency());
        voucherCommand.setAmount(total);
        voucherCommand.setOperatorId(operatorId);
        voucherCommand.setRemark("采购退货 " + purchaseReturn.getDocNo());
        autoVoucherAppService.postPurchaseReturn(voucherCommand);
        purchaseReturnRepository.update(purchaseReturn);
        return PurchaseReturnView.of(purchaseReturn);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnView cancel(Long id) {
        PurchaseReturn purchaseReturn = requireReturn(id);
        purchaseReturn.cancel();
        purchaseReturnRepository.update(purchaseReturn);
        return PurchaseReturnView.of(purchaseReturn);
    }

    /**
     * 按主键查询退货单，不存在抛业务异常。
     *
     * @param id 主键
     * @return 采购退货单
     */
    private PurchaseReturn requireReturn(Long id) {
        return purchaseReturnRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PURCHASE_RETURN_NOT_FOUND));
    }

    /**
     * 按主键查询源订单，不存在抛业务异常。
     *
     * @param id 采购订单主键
     * @return 采购订单
     */
    private PurchaseOrder requireOrder(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PURCHASE_ORDER_NOT_FOUND));
    }

    /**
     * 构造采购退货出库流水。
     *
     * @param docNo 退货单号
     * @param line 退货分录
     * @param warehouseId 仓库 ID
     * @param amount 出库成本
     * @param operatorId 操作人 ID
     * @return 流水
     */
    private StockMovement movement(String docNo, PurchaseReturnLine line, Long warehouseId,
                                   BigDecimal amount, Long operatorId) {
        StockMovement movement = new StockMovement();
        movement.setDocNo(docNo);
        movement.setSourceType(ScmStatus.SOURCE_PURCHASE_RETURN);
        movement.setSourceId(docNo);
        movement.setItemId(line.getItemId());
        movement.setItemCode(line.getItemCode());
        movement.setItemName(line.getItemName());
        movement.setWarehouseId(warehouseId);
        movement.setDirection(ScmStatus.OUT);
        movement.setQuantity(line.getQuantity());
        movement.setUnitCost(line.getUnitCost());
        movement.setAmount(amount.setScale(Amounts.SCALE, RoundingMode.HALF_UP));
        movement.setOccurredAt(LocalDateTime.now());
        movement.setRemark("操作人 " + (operatorId == null ? "-" : operatorId));
        return movement;
    }
}

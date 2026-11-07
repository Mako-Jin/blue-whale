package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.scm.application.command.SalesReturnCreateCommand;
import com.bluewhale.erp.scm.application.command.SalesReturnInternalCreateCommand;
import com.bluewhale.erp.scm.application.service.SalesReturnAppService;
import com.bluewhale.erp.scm.application.view.SalesReturnView;
import com.bluewhale.erp.scm.domain.model.SalesOrder;
import com.bluewhale.erp.scm.domain.model.SalesOrderLine;
import com.bluewhale.erp.scm.domain.model.SalesReturn;
import com.bluewhale.erp.scm.domain.model.SalesReturnLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.repository.SalesOrderRepository;
import com.bluewhale.erp.scm.domain.repository.SalesReturnRepository;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售退货应用服务实现。
 *
 * <p>过账闭环：库存入库（按当前移动加权均价）→ 应收冲减 → 红字凭证，同一事务内完成。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SalesReturnAppServiceImpl implements SalesReturnAppService {

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    /** CRM 退货单号前缀 */
    private static final String CRM_DOC_PREFIX = "CRM-SR-";

    /** 来源系统类型：CRM */
    private static final String SOURCE_TYPE_CRM = "CRM";

    /** 成本小数位 */
    private static final int COST_SCALE = 4;

    private final SalesReturnRepository salesReturnRepository;

    private final SalesOrderRepository salesOrderRepository;

    private final StockRepository stockRepository;

    private final ArApAppService arApAppService;

    private final AutoVoucherAppService autoVoucherAppService;

    @Override
    public PageResult<SalesReturnView> page(ScmQuery query) {
        List<SalesReturnView> records = salesReturnRepository.page(query).stream()
                .map(SalesReturnView::of).toList();
        return PageResult.of(records, salesReturnRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public SalesReturnView detail(Long id) {
        return SalesReturnView.of(requireReturn(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesReturnView create(SalesReturnCreateCommand command) {
        SalesOrder order = requireOrder(command.getSalesOrderId());
        if (!ScmStatus.PARTIALLY_SHIPPED.equals(order.getStatus())
                && !ScmStatus.SHIPPED.equals(order.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.RETURN_INVALID, "销售订单尚未发货，不能退货");
        }
        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setSalesOrderId(order.getId());
        salesReturn.setCustomerId(order.getCustomerId());
        salesReturn.setCustomerName(order.getCustomerName());
        salesReturn.setDocDate(command.getDocDate());
        salesReturn.setWarehouseId(order.getWarehouseId());
        salesReturn.setStatus(ScmStatus.DRAFT);
        salesReturn.setCurrency(order.getCurrency() == null ? DEFAULT_CURRENCY : order.getCurrency());
        salesReturn.setRemark(command.getRemark());
        List<SalesReturnLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (SalesReturnCreateCommand.Line lineCommand : command.getLines()) {
            SalesOrderLine orderLine = order.findLine(lineCommand.getOrderLineNo());
            BigDecimal shipped = orderLine.getShippedQuantity() == null
                    ? BigDecimal.ZERO : orderLine.getShippedQuantity();
            if (shipped.signum() == 0) {
                throw ErpErrorCode.exception(ErpErrorCode.RETURN_INVALID,
                        "订单第 " + orderLine.getLineNo() + " 行尚未发货");
            }
            BigDecimal already = salesReturnRepository.sumReturnedQuantity(order.getId(), orderLine.getLineNo());
            if (already.add(lineCommand.getQuantity()).compareTo(shipped) > 0) {
                throw ErpErrorCode.exception(ErpErrorCode.RETURN_QUANTITY_EXCEEDED,
                        "订单第 " + orderLine.getLineNo() + " 行可退数量 " + shipped.subtract(already));
            }
            SalesReturnLine line = new SalesReturnLine();
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
        salesReturn.setLines(lines);
        salesReturn.calculateTotal();
        salesReturn.setDocNo(StringUtils.hasText(command.getDocNo())
                ? command.getDocNo() : salesReturnRepository.nextDocNo(command.getDocDate()));
        salesReturn.setCrmReturnNo(command.getCrmReturnNo());
        salesReturn.setSourceType(command.getSourceType());
        salesReturn.setSourceId(command.getSourceId());
        salesReturnRepository.insert(salesReturn);
        return SalesReturnView.of(salesReturn);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesReturnView createInternal(SalesReturnInternalCreateCommand command) {
        SalesReturnView existing = findByCrmReturnNo(command.getCrmReturnNo());
        if (existing != null) {
            return existing;
        }
        SalesOrder order = resolveOrder(command);
        SalesReturnCreateCommand createCommand = new SalesReturnCreateCommand();
        createCommand.setSalesOrderId(order.getId());
        createCommand.setDocDate(command.getDocDate());
        createCommand.setRemark(command.getRemark());
        createCommand.setDocNo(CRM_DOC_PREFIX + command.getCrmReturnNo());
        createCommand.setCrmReturnNo(command.getCrmReturnNo());
        createCommand.setSourceType(SOURCE_TYPE_CRM);
        createCommand.setSourceId(command.getCrmReturnNo());
        for (SalesReturnInternalCreateCommand.Line line : command.getLines()) {
            SalesReturnCreateCommand.Line target = new SalesReturnCreateCommand.Line();
            target.setOrderLineNo(line.getOrderLineNo());
            target.setQuantity(line.getQuantity());
            createCommand.getLines().add(target);
        }
        try {
            SalesReturnView created = create(createCommand);
            return post(created.getId(), null);
        } catch (DuplicateKeyException exception) {
            SalesReturnView concurrent = findByCrmReturnNo(command.getCrmReturnNo());
            if (concurrent != null) {
                return concurrent;
            }
            throw exception;
        }
    }

    @Override
    public SalesReturnView findByCrmReturnNo(String crmReturnNo) {
        return salesReturnRepository.findByCrmReturnNo(crmReturnNo).map(SalesReturnView::of).orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesReturnView post(Long id, Long operatorId) {
        SalesReturn salesReturn = requireReturn(id);
        salesReturn.post();
        SalesOrder order = requireOrder(salesReturn.getSalesOrderId());
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal costTotal = BigDecimal.ZERO;
        for (SalesReturnLine line : salesReturn.getLines()) {
            SalesOrderLine orderLine = order.findLine(line.getOrderLineNo());
            BigDecimal shipped = orderLine.getShippedQuantity() == null
                    ? BigDecimal.ZERO : orderLine.getShippedQuantity();
            BigDecimal returned = salesReturnRepository.sumReturnedQuantity(salesReturn.getSalesOrderId(),
                    line.getOrderLineNo());
            if (returned.compareTo(shipped) > 0) {
                throw ErpErrorCode.exception(ErpErrorCode.RETURN_QUANTITY_EXCEEDED,
                        "订单第 " + line.getOrderLineNo() + " 行累计退货超过已发数量");
            }
            StockBalance balance = stockRepository.findBalance(line.getItemId(), salesReturn.getWarehouseId())
                    .orElseGet(() -> newBalance(line, salesReturn.getWarehouseId()));
            BigDecimal unitCost = averageCost(balance);
            BigDecimal cost = balance.inbound(line.getQuantity(), unitCost);
            stockRepository.saveBalance(balance);
            line.setUnitCost(unitCost);
            line.setCostAmount(cost);
            total = total.add(line.getAmount());
            costTotal = costTotal.add(cost);
            stockRepository.insertMovement(movement(salesReturn.getDocNo(), line, salesReturn.getWarehouseId(),
                    unitCost, cost, operatorId));
        }
        salesReturn.setTotalAmount(total);
        arApAppService.offsetReceivable(salesReturn.getCustomerId(), total);
        AutoVoucherCommand voucherCommand = new AutoVoucherCommand();
        voucherCommand.setSourceType(ScmStatus.SOURCE_SALES_RETURN);
        voucherCommand.setSourceId(salesReturn.getDocNo());
        voucherCommand.setVoucherDate(salesReturn.getDocDate());
        voucherCommand.setCurrency(salesReturn.getCurrency());
        voucherCommand.setAmount(total);
        voucherCommand.setCostAmount(costTotal);
        voucherCommand.setOperatorId(operatorId);
        voucherCommand.setRemark("销售退货 " + salesReturn.getDocNo());
        autoVoucherAppService.postSalesReturn(voucherCommand);
        salesReturnRepository.update(salesReturn);
        return SalesReturnView.of(salesReturn);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesReturnView cancel(Long id) {
        SalesReturn salesReturn = requireReturn(id);
        salesReturn.cancel();
        salesReturnRepository.update(salesReturn);
        return SalesReturnView.of(salesReturn);
    }

    /**
     * 按主键查询退货单，不存在抛业务异常。
     *
     * @param id 主键
     * @return 销售退货单
     */
    private SalesReturn requireReturn(Long id) {
        return salesReturnRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.SALES_RETURN_NOT_FOUND));
    }

    /**
     * 解析内网命令中的源订单。
     *
     * @param command 内网创建命令
     * @return 销售订单
     */
    private SalesOrder resolveOrder(SalesReturnInternalCreateCommand command) {
        if (command.getSalesOrderId() != null) {
            return requireOrder(command.getSalesOrderId());
        }
        if (StringUtils.hasText(command.getErpOrderNo())) {
            return salesOrderRepository.findByDocNo(command.getErpOrderNo())
                    .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.SALES_ORDER_NOT_FOUND));
        }
        throw ErpErrorCode.exception(ErpErrorCode.SALES_ORDER_NOT_FOUND);
    }

    /**
     * 按主键查询源订单，不存在抛业务异常。
     *
     * @param id 销售订单主键
     * @return 销售订单
     */
    private SalesOrder requireOrder(Long id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.SALES_ORDER_NOT_FOUND));
    }

    /**
     * 计算当前移动加权平均单位成本。
     *
     * @param balance 库存余额
     * @return 单位成本，无余额返回 0
     */
    private BigDecimal averageCost(StockBalance balance) {
        BigDecimal quantity = balance.getQuantity() == null ? BigDecimal.ZERO : balance.getQuantity();
        BigDecimal amount = balance.getAmount() == null ? BigDecimal.ZERO : balance.getAmount();
        if (quantity.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return amount.divide(quantity, COST_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 构造新余额对象。
     *
     * @param line 退货分录
     * @param warehouseId 仓库 ID
     * @return 余额
     */
    private StockBalance newBalance(SalesReturnLine line, Long warehouseId) {
        StockBalance balance = new StockBalance();
        balance.setItemId(line.getItemId());
        balance.setItemCode(line.getItemCode());
        balance.setItemName(line.getItemName());
        balance.setWarehouseId(warehouseId);
        balance.setQuantity(BigDecimal.ZERO);
        balance.setAmount(BigDecimal.ZERO);
        return balance;
    }

    /**
     * 构造销售退货入库流水。
     *
     * @param docNo 退货单号
     * @param line 退货分录
     * @param warehouseId 仓库 ID
     * @param unitCost 单位成本
     * @param amount 入库成本
     * @param operatorId 操作人 ID
     * @return 流水
     */
    private StockMovement movement(String docNo, SalesReturnLine line, Long warehouseId, BigDecimal unitCost,
                                   BigDecimal amount, Long operatorId) {
        StockMovement movement = new StockMovement();
        movement.setDocNo(docNo);
        movement.setSourceType(ScmStatus.SOURCE_SALES_RETURN);
        movement.setSourceId(docNo);
        movement.setItemId(line.getItemId());
        movement.setItemCode(line.getItemCode());
        movement.setItemName(line.getItemName());
        movement.setWarehouseId(warehouseId);
        movement.setDirection(ScmStatus.IN);
        movement.setQuantity(line.getQuantity());
        movement.setUnitCost(unitCost);
        movement.setAmount(amount.setScale(Amounts.SCALE, RoundingMode.HALF_UP));
        movement.setOccurredAt(LocalDateTime.now());
        movement.setRemark("操作人 " + (operatorId == null ? "-" : operatorId));
        return movement;
    }
}

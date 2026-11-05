package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.command.PayableCreateCommand;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.PurchaseOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.ReceiveCommand;
import com.bluewhale.erp.scm.application.service.PriceListAppService;
import com.bluewhale.erp.scm.application.service.PurchaseAppService;
import com.bluewhale.erp.scm.application.view.PriceListView;
import com.bluewhale.erp.scm.application.view.PurchaseOrderView;
import com.bluewhale.erp.scm.domain.model.PriceType;
import com.bluewhale.erp.scm.domain.model.PurchaseOrder;
import com.bluewhale.erp.scm.domain.model.PurchaseOrderLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.port.SupplierCatalog;
import com.bluewhale.erp.scm.domain.port.SupplierSnapshot;
import com.bluewhale.erp.scm.domain.repository.PurchaseOrderRepository;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 采购应用服务实现。
 *
 * <p>收货闭环：订单进度 → 库存入库 → 应付台账 → 自动凭证，全部在同一事务内完成；
 * 应付与凭证都以收货单号做幂等键。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PurchaseAppServiceImpl implements PurchaseAppService {

    /** 收货单号前缀 */
    private static final String RECEIPT_PREFIX = "GR-";

    /** 收货单号随机后缀长度 */
    private static final int DOC_SUFFIX_LENGTH = 10;

    /** 默认账期天数 */
    private static final int PAYMENT_TERM_DAYS = 30;

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final SupplierCatalog supplierCatalog;

    private final ItemCatalog itemCatalog;

    private final WarehouseRepository warehouseRepository;

    private final StockRepository stockRepository;

    private final ArApAppService arApAppService;

    private final AutoVoucherAppService autoVoucherAppService;

    private final PriceListAppService priceListAppService;

    @Override
    public PageResult<PurchaseOrderView> page(ScmQuery query) {
        List<PurchaseOrderView> records = purchaseOrderRepository.page(query).stream()
                .map(PurchaseOrderView::of).toList();
        return PageResult.of(records, purchaseOrderRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public PurchaseOrderView detail(Long id) {
        return PurchaseOrderView.of(requireOrder(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderView create(PurchaseOrderCreateCommand command) {
        SupplierSnapshot supplier = supplierCatalog.findById(command.getSupplierId())
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.SUPPLIER_INVALID));
        if (!MasterDataStatus.ENABLED.equals(supplier.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.SUPPLIER_INVALID);
        }
        requireWarehouse(command.getWarehouseId());
        PurchaseOrder order = new PurchaseOrder();
        order.setDocNo(purchaseOrderRepository.nextDocNo(command.getDocDate()));
        order.setSupplierId(supplier.getId());
        order.setSupplierName(supplier.getName());
        order.setDocDate(command.getDocDate());
        order.setExpectedDate(command.getExpectedDate());
        order.setWarehouseId(command.getWarehouseId());
        order.setStatus(ScmStatus.DRAFT);
        order.setCurrency(StringUtils.hasText(command.getCurrency()) ? command.getCurrency() : DEFAULT_CURRENCY);
        order.setRemark(command.getRemark());
        List<PurchaseOrderLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (PurchaseOrderCreateCommand.Line lineCommand : command.getLines()) {
            ItemSnapshot item = itemCatalog.findByCode(lineCommand.getItemCode())
                    .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID,
                            "物料 " + lineCommand.getItemCode() + " 不存在"));
            if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
                throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + item.getCode() + " 已停用");
            }
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setLineNo(lineNo++);
            line.setItemId(item.getId());
            line.setItemCode(item.getCode());
            line.setItemName(item.getName());
            line.setUom(lineCommand.getUom());
            line.setQuantity(lineCommand.getQuantity());
            line.setReceivedQuantity(BigDecimal.ZERO);
            line.setUnitPrice(resolveUnitPrice(lineCommand.getUnitPrice(), supplier.getId(),
                    item.getId(), command.getDocDate()));
            lines.add(line);
        }
        order.setLines(lines);
        order.calculateTotal();
        purchaseOrderRepository.insert(order);
        return PurchaseOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderView approve(Long id) {
        PurchaseOrder order = requireOrder(id);
        order.approve();
        purchaseOrderRepository.update(order);
        return PurchaseOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderView receive(Long id, ReceiveCommand command, Long operatorId) {
        PurchaseOrder order = requireOrder(id);
        Map<Integer, BigDecimal> quantities = new LinkedHashMap<>();
        for (ReceiveCommand.Line line : command.getLines()) {
            quantities.merge(line.getLineNo(), line.getQuantity(), BigDecimal::add);
        }
        order.receive(quantities);
        String docNo = RECEIPT_PREFIX + UUID.randomUUID().toString().replace("-", "")
                .substring(0, DOC_SUFFIX_LENGTH).toUpperCase();
        LocalDate receiveDate = command.getReceiveDate();
        BigDecimal amount = BigDecimal.ZERO;
        for (Map.Entry<Integer, BigDecimal> entry : quantities.entrySet()) {
            PurchaseOrderLine line = order.findLine(entry.getKey());
            BigDecimal quantity = entry.getValue();
            BigDecimal lineAmount = line.getUnitPrice().multiply(quantity)
                    .setScale(Amounts.SCALE, RoundingMode.HALF_UP);
            amount = amount.add(lineAmount);
            stockRepository.insertMovement(movement(docNo, ScmStatus.SOURCE_GOODS_RECEIPT,
                    line.getItemId(), line.getItemCode(), line.getItemName(), order.getWarehouseId(),
                    ScmStatus.IN, quantity, line.getUnitPrice(), lineAmount, receiveDate, operatorId));
            StockBalance balance = stockRepository.findBalance(line.getItemId(), order.getWarehouseId())
                    .orElseGet(() -> newBalance(line, order.getWarehouseId()));
            balance.inbound(quantity, line.getUnitPrice());
            stockRepository.saveBalance(balance);
        }
        purchaseOrderRepository.update(order);
        PayableCreateCommand payableCommand = new PayableCreateCommand();
        payableCommand.setSupplierId(order.getSupplierId());
        payableCommand.setSupplierName(order.getSupplierName());
        payableCommand.setDocDate(receiveDate);
        payableCommand.setDueDate(receiveDate.plusDays(PAYMENT_TERM_DAYS));
        payableCommand.setAmount(amount);
        payableCommand.setCurrency(order.getCurrency());
        payableCommand.setSourceType(ScmStatus.SOURCE_GOODS_RECEIPT);
        payableCommand.setSourceId(docNo);
        payableCommand.setRemark("采购收货 " + docNo);
        arApAppService.createPayable(payableCommand);
        AutoVoucherCommand voucherCommand = new AutoVoucherCommand();
        voucherCommand.setSourceType(ScmStatus.SOURCE_GOODS_RECEIPT);
        voucherCommand.setSourceId(docNo);
        voucherCommand.setVoucherDate(receiveDate);
        voucherCommand.setCurrency(order.getCurrency());
        voucherCommand.setAmount(amount);
        voucherCommand.setOperatorId(operatorId);
        voucherCommand.setRemark("采购收货 " + docNo);
        autoVoucherAppService.postReceipt(voucherCommand);
        return PurchaseOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderView cancel(Long id) {
        PurchaseOrder order = requireOrder(id);
        order.cancel();
        purchaseOrderRepository.update(order);
        return PurchaseOrderView.of(order);
    }

    /**
     * 单价为空时按供应商采购价兜底取价。
     *
     * @param unitPrice 显式单价
     * @param supplierId 供应商主键
     * @param itemId 物料主键
     * @param docDate 单据日期
     * @return 单价
     */
    private BigDecimal resolveUnitPrice(BigDecimal unitPrice, Long supplierId, Long itemId, LocalDate docDate) {
        if (unitPrice != null) {
            return unitPrice;
        }
        PriceListView priceList = priceListAppService.resolve(PriceType.PURCHASE, supplierId, itemId, docDate);
        return priceList.getPrice();
    }

    /**
     * 按主键查询订单，不存在抛业务异常。
     *
     * @param id 订单 ID
     * @return 采购订单
     */
    private PurchaseOrder requireOrder(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PURCHASE_ORDER_NOT_FOUND));
    }

    /**
     * 校验仓库存在。
     *
     * @param warehouseId 仓库 ID
     */
    private void requireWarehouse(Long warehouseId) {
        warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.WAREHOUSE_NOT_FOUND));
    }

    /**
     * 构造新余额对象。
     *
     * @param line 分录
     * @param warehouseId 仓库 ID
     * @return 余额
     */
    private StockBalance newBalance(PurchaseOrderLine line, Long warehouseId) {
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
     * 构造库存流水。
     *
     * @param docNo 单据号
     * @param sourceType 来源类型
     * @param itemId 物料 ID
     * @param itemCode 物料编码
     * @param itemName 物料名称
     * @param warehouseId 仓库 ID
     * @param direction 方向
     * @param quantity 数量
     * @param unitCost 单位成本
     * @param amount 金额
     * @param occurredDate 发生日期
     * @param operatorId 操作人 ID
     * @return 流水
     */
    private StockMovement movement(String docNo, String sourceType, Long itemId, String itemCode,
                                   String itemName, Long warehouseId, String direction, BigDecimal quantity,
                                   BigDecimal unitCost, BigDecimal amount, LocalDate occurredDate,
                                   Long operatorId) {
        StockMovement movement = new StockMovement();
        movement.setDocNo(docNo);
        movement.setSourceType(sourceType);
        movement.setSourceId(docNo);
        movement.setItemId(itemId);
        movement.setItemCode(itemCode);
        movement.setItemName(itemName);
        movement.setWarehouseId(warehouseId);
        movement.setDirection(direction);
        movement.setQuantity(quantity);
        movement.setUnitCost(unitCost);
        movement.setAmount(amount);
        LocalDateTime occurredAt = occurredDate == null ? LocalDateTime.now() : occurredDate.atStartOfDay();
        movement.setOccurredAt(occurredAt);
        movement.setRemark("操作人 " + (operatorId == null ? "-" : operatorId));
        return movement;
    }
}

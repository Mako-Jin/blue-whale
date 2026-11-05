package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.command.ReceivableCreateCommand;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.masterdata.domain.model.Customer;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.repository.CustomerRepository;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.SalesOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.ShipCommand;
import com.bluewhale.erp.scm.application.service.PriceListAppService;
import com.bluewhale.erp.scm.application.service.SalesAppService;
import com.bluewhale.erp.scm.application.view.PriceListView;
import com.bluewhale.erp.scm.application.view.SalesOrderView;
import com.bluewhale.erp.scm.domain.model.PriceType;
import com.bluewhale.erp.scm.domain.model.SalesOrder;
import com.bluewhale.erp.scm.domain.model.SalesOrderLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.SalesOrderRepository;
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
 * 销售应用服务实现。
 *
 * <p>发货闭环：信用额度校验（未结应收 + 在途预占）→ 库存出库（移动加权成本）→ 应收台账 → 自动凭证。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SalesAppServiceImpl implements SalesAppService {

    /** 发货单号前缀 */
    private static final String SHIPMENT_PREFIX = "DN-";

    /** 单据号随机后缀长度 */
    private static final int DOC_SUFFIX_LENGTH = 10;

    /** 默认账期天数 */
    private static final int PAYMENT_TERM_DAYS = 30;

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    /** 单位成本小数位 */
    private static final int COST_SCALE = 4;

    private final SalesOrderRepository salesOrderRepository;

    private final ItemCatalog itemCatalog;

    private final CustomerRepository customerRepository;

    private final WarehouseRepository warehouseRepository;

    private final StockRepository stockRepository;

    private final ArApAppService arApAppService;

    private final AutoVoucherAppService autoVoucherAppService;

    private final PriceListAppService priceListAppService;

    @Override
    public PageResult<SalesOrderView> page(ScmQuery query) {
        List<SalesOrderView> records = salesOrderRepository.page(query).stream()
                .map(SalesOrderView::of).toList();
        return PageResult.of(records, salesOrderRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public SalesOrderView detail(Long id) {
        return SalesOrderView.of(requireOrder(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderView create(SalesOrderCreateCommand command) {
        Customer customer = requireCustomer(command.getCustomerId());
        requireWarehouse(command.getWarehouseId());
        SalesOrder order = new SalesOrder();
        order.setDocNo(salesOrderRepository.nextDocNo(command.getDocDate()));
        order.setCustomerId(customer.getId());
        order.setCustomerName(customer.getName());
        order.setDocDate(command.getDocDate());
        order.setDeliveryDate(command.getDeliveryDate());
        order.setWarehouseId(command.getWarehouseId());
        order.setStatus(ScmStatus.DRAFT);
        order.setCurrency(StringUtils.hasText(command.getCurrency()) ? command.getCurrency() : DEFAULT_CURRENCY);
        order.setRemark(command.getRemark());
        List<SalesOrderLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (SalesOrderCreateCommand.Line lineCommand : command.getLines()) {
            ItemSnapshot item = itemCatalog.findByCode(lineCommand.getItemCode())
                    .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID,
                            "物料 " + lineCommand.getItemCode() + " 不存在"));
            if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
                throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + item.getCode() + " 已停用");
            }
            SalesOrderLine line = new SalesOrderLine();
            line.setLineNo(lineNo++);
            line.setItemId(item.getId());
            line.setItemCode(item.getCode());
            line.setItemName(item.getName());
            line.setUom(lineCommand.getUom());
            line.setQuantity(lineCommand.getQuantity());
            line.setShippedQuantity(BigDecimal.ZERO);
            line.setUnitPrice(resolveUnitPrice(lineCommand.getUnitPrice(), customer.getId(),
                    item.getId(), command.getDocDate()));
            lines.add(line);
        }
        order.setLines(lines);
        order.calculateTotal();
        salesOrderRepository.insert(order);
        return SalesOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderView approve(Long id) {
        SalesOrder order = requireOrder(id);
        checkCreditLimit(order);
        order.approve();
        salesOrderRepository.update(order);
        return SalesOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderView ship(Long id, ShipCommand command, Long operatorId) {
        SalesOrder order = requireOrder(id);
        Map<Integer, BigDecimal> quantities = new LinkedHashMap<>();
        for (ShipCommand.Line line : command.getLines()) {
            quantities.merge(line.getLineNo(), line.getQuantity(), BigDecimal::add);
        }
        order.ship(quantities);
        String docNo = SHIPMENT_PREFIX + UUID.randomUUID().toString().replace("-", "")
                .substring(0, DOC_SUFFIX_LENGTH).toUpperCase();
        LocalDate shipDate = command.getShipDate();
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal costTotal = BigDecimal.ZERO;
        for (Map.Entry<Integer, BigDecimal> entry : quantities.entrySet()) {
            SalesOrderLine line = order.findLine(entry.getKey());
            BigDecimal quantity = entry.getValue();
            BigDecimal lineAmount = line.getUnitPrice().multiply(quantity)
                    .setScale(Amounts.SCALE, RoundingMode.HALF_UP);
            amount = amount.add(lineAmount);
            StockBalance balance = stockRepository.findBalance(line.getItemId(), order.getWarehouseId())
                    .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.STOCK_NOT_FOUND,
                            "物料 " + line.getItemCode() + " 在该仓库无库存"));
            BigDecimal cost = balance.outbound(quantity);
            costTotal = costTotal.add(cost);
            BigDecimal unitCost = quantity.signum() == 0 ? BigDecimal.ZERO
                    : cost.divide(quantity, COST_SCALE, RoundingMode.HALF_UP);
            stockRepository.insertMovement(movement(docNo, line, order.getWarehouseId(),
                    quantity, unitCost, cost, shipDate, operatorId));
            stockRepository.saveBalance(balance);
        }
        checkShipmentCreditLimit(order);
        salesOrderRepository.update(order);
        ReceivableCreateCommand receivableCommand = new ReceivableCreateCommand();
        receivableCommand.setCustomerId(order.getCustomerId());
        receivableCommand.setDocDate(shipDate);
        receivableCommand.setDueDate(shipDate.plusDays(PAYMENT_TERM_DAYS));
        receivableCommand.setAmount(amount);
        receivableCommand.setCurrency(order.getCurrency());
        receivableCommand.setSourceType(ScmStatus.SOURCE_SHIPMENT);
        receivableCommand.setSourceId(docNo);
        receivableCommand.setRemark("销售发货 " + docNo);
        arApAppService.createReceivable(receivableCommand);
        AutoVoucherCommand voucherCommand = new AutoVoucherCommand();
        voucherCommand.setSourceType(ScmStatus.SOURCE_SHIPMENT);
        voucherCommand.setSourceId(docNo);
        voucherCommand.setVoucherDate(shipDate);
        voucherCommand.setCurrency(order.getCurrency());
        voucherCommand.setAmount(amount);
        voucherCommand.setCostAmount(costTotal);
        voucherCommand.setOperatorId(operatorId);
        voucherCommand.setRemark("销售发货 " + docNo);
        autoVoucherAppService.postShipment(voucherCommand);
        return SalesOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderView cancel(Long id) {
        SalesOrder order = requireOrder(id);
        order.cancel();
        salesOrderRepository.update(order);
        return SalesOrderView.of(order);
    }

    /**
     * 审核前校验信用额度：未结应收 + 在途预占 + 本单金额。
     *
     * <p>在途预占为已审核或部分发货订单的未发货金额，草稿与已取消订单不占用额度。</p>
     *
     * @param order 销售订单
     */
    private void checkCreditLimit(SalesOrder order) {
        Customer customer = requireCustomer(order.getCustomerId());
        BigDecimal limit = customer.getCreditLimit() == null ? BigDecimal.ZERO : customer.getCreditLimit();
        if (limit.signum() <= 0) {
            return;
        }
        BigDecimal outstanding = arApAppService.outstandingReceivable(customer.getId());
        BigDecimal reserved = salesOrderRepository.reservedAmount(customer.getId());
        BigDecimal occupied = outstanding.add(reserved);
        if (occupied.add(order.getTotalAmount()).compareTo(limit) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.CREDIT_LIMIT_EXCEEDED,
                    "客户信用额度不足：额度 " + limit + "，已占用 " + occupied + "（含在途预占 " + reserved + "）");
        }
    }

    /**
     * 发货前校验信用额度：未结应收 + 在途预占（在途预占已包含本单未发货金额）。
     *
     * <p>发货后本次金额转为应收、预占同步减少，因此不再叠加本次发货金额，避免重复占用。</p>
     *
     * @param order 销售订单
     */
    private void checkShipmentCreditLimit(SalesOrder order) {
        Customer customer = requireCustomer(order.getCustomerId());
        BigDecimal limit = customer.getCreditLimit() == null ? BigDecimal.ZERO : customer.getCreditLimit();
        if (limit.signum() <= 0) {
            return;
        }
        BigDecimal outstanding = arApAppService.outstandingReceivable(customer.getId());
        BigDecimal reserved = salesOrderRepository.reservedAmount(customer.getId());
        if (outstanding.add(reserved).compareTo(limit) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.CREDIT_LIMIT_EXCEEDED,
                    "客户信用额度不足：额度 " + limit + "，已占用 " + outstanding.add(reserved)
                            + "（含在途预占 " + reserved + "）");
        }
    }

    /**
     * 单价为空时按客户售价兜底取价。
     *
     * @param unitPrice 显式单价
     * @param customerId 客户主键
     * @param itemId 物料主键
     * @param docDate 单据日期
     * @return 单价
     */
    private BigDecimal resolveUnitPrice(BigDecimal unitPrice, Long customerId, Long itemId, LocalDate docDate) {
        if (unitPrice != null) {
            return unitPrice;
        }
        PriceListView priceList = priceListAppService.resolve(PriceType.SALES, customerId, itemId, docDate);
        return priceList.getPrice();
    }

    /**
     * 按主键查询订单，不存在抛业务异常。
     *
     * @param id 订单 ID
     * @return 销售订单
     */
    private SalesOrder requireOrder(Long id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.SALES_ORDER_NOT_FOUND));
    }

    /**
     * 查询启用状态的客户。
     *
     * @param customerId 客户 ID
     * @return 客户
     */
    private Customer requireCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.CUSTOMER_NOT_FOUND));
        if (!customer.enabled()) {
            throw ErpErrorCode.exception(ErpErrorCode.CUSTOMER_INVALID);
        }
        return customer;
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
     * 构造出库流水。
     *
     * @param docNo 发货单号
     * @param line 订单分录
     * @param warehouseId 仓库 ID
     * @param quantity 数量
     * @param unitCost 单位成本
     * @param cost 成本金额
     * @param shipDate 发货日期
     * @param operatorId 操作人 ID
     * @return 流水
     */
    private StockMovement movement(String docNo, SalesOrderLine line, Long warehouseId, BigDecimal quantity,
                                   BigDecimal unitCost, BigDecimal cost, LocalDate shipDate, Long operatorId) {
        StockMovement movement = new StockMovement();
        movement.setDocNo(docNo);
        movement.setSourceType(ScmStatus.SOURCE_SHIPMENT);
        movement.setSourceId(docNo);
        movement.setItemId(line.getItemId());
        movement.setItemCode(line.getItemCode());
        movement.setItemName(line.getItemName());
        movement.setWarehouseId(warehouseId);
        movement.setDirection(ScmStatus.OUT);
        movement.setQuantity(quantity);
        movement.setUnitCost(unitCost);
        movement.setAmount(cost);
        LocalDateTime occurredAt = shipDate == null ? LocalDateTime.now() : shipDate.atStartOfDay();
        movement.setOccurredAt(occurredAt);
        movement.setRemark("操作人 " + (operatorId == null ? "-" : operatorId));
        return movement;
    }
}

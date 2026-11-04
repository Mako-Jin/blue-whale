package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.masterdata.domain.model.Customer;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.CustomerRepository;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.SalesOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.ShipCommand;
import com.bluewhale.erp.scm.application.view.SalesOrderView;
import com.bluewhale.erp.scm.domain.model.SalesOrder;
import com.bluewhale.erp.scm.domain.model.SalesOrderLine;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.SalesOrderRepository;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SalesAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class SalesAppServiceImplTest {

    private final SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);

    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);

    private final StockRepository stockRepository = mock(StockRepository.class);

    private final ArApAppService arApAppService = mock(ArApAppService.class);

    private final AutoVoucherAppService autoVoucherAppService = mock(AutoVoucherAppService.class);

    private final SalesAppServiceImpl service = new SalesAppServiceImpl(salesOrderRepository, itemCatalog,
            customerRepository, warehouseRepository, stockRepository, arApAppService, autoVoucherAppService);

    @Test
    @DisplayName("新增销售订单：快照客户与物料")
    void shouldCreateSalesOrder() {
        when(customerRepository.findById(7201L)).thenReturn(Optional.of(customer("0")));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(warehouseRepository.findById(7301L)).thenReturn(Optional.of(warehouse()));
        when(salesOrderRepository.nextDocNo(LocalDate.of(2026, 11, 4))).thenReturn("SO-20261104-0001");

        SalesOrderView view = service.create(command());

        assertEquals("SO-20261104-0001", view.getDocNo());
        assertEquals(ScmStatus.DRAFT, view.getStatus());
        assertEquals("客户A", view.getCustomerName());
        assertEquals(new BigDecimal("20.00"), view.getTotalAmount());
        verify(salesOrderRepository).insert(any(SalesOrder.class));
    }

    @Test
    @DisplayName("信用额度不足时审核拒绝")
    void shouldRejectApproveWhenCreditExceeded() {
        SalesOrder order = draftOrder();
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(customerRepository.findById(7201L)).thenReturn(Optional.of(customer("100")));
        when(arApAppService.outstandingReceivable(7201L)).thenReturn(new BigDecimal("90"));

        BizException exception = assertThrows(BizException.class, () -> service.approve(1L));

        assertEquals(ErpErrorCode.CREDIT_LIMIT_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("审核通过：状态变为已审核")
    void shouldApproveWhenNoCreditLimit() {
        SalesOrder order = draftOrder();
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(customerRepository.findById(7201L)).thenReturn(Optional.of(customer("0")));

        SalesOrderView view = service.approve(1L);

        assertEquals(ScmStatus.APPROVED, view.getStatus());
        verify(salesOrderRepository).update(any(SalesOrder.class));
    }

    @Test
    @DisplayName("发货：出库结转成本、生成应收并自动记账")
    void shouldShipAndPostReceivable() {
        SalesOrder order = approvedOrder();
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(customerRepository.findById(7201L)).thenReturn(Optional.of(customer("0")));
        StockBalance balance = new StockBalance();
        balance.setItemId(3L);
        balance.setWarehouseId(7301L);
        balance.setQuantity(new BigDecimal("10"));
        balance.setAmount(new BigDecimal("100"));
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.of(balance));

        SalesOrderView view = service.ship(1L, shipCommand(), 99L);

        assertEquals(ScmStatus.SHIPPED, view.getStatus());
        assertEquals(0, balance.getQuantity().compareTo(new BigDecimal("5")));
        verify(stockRepository).insertMovement(any());
        verify(arApAppService).createReceivable(any());
        verify(autoVoucherAppService).postShipment(any());
    }

    @Test
    @DisplayName("无库存时发货拒绝")
    void shouldRejectShipWithoutStock() {
        SalesOrder order = approvedOrder();
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.ship(1L, shipCommand(), 99L));

        assertEquals(ErpErrorCode.STOCK_NOT_FOUND, exception.getCode());
    }

    /**
     * 构造创建命令。
     *
     * @return 命令
     */
    private SalesOrderCreateCommand command() {
        SalesOrderCreateCommand command = new SalesOrderCreateCommand();
        command.setCustomerId(7201L);
        command.setDocDate(LocalDate.of(2026, 11, 4));
        command.setWarehouseId(7301L);
        SalesOrderCreateCommand.Line line = new SalesOrderCreateCommand.Line();
        line.setItemCode("ERP-001");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("10"));
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造发货命令。
     *
     * @return 命令
     */
    private ShipCommand shipCommand() {
        ShipCommand command = new ShipCommand();
        command.setShipDate(LocalDate.of(2026, 11, 4));
        ShipCommand.Line line = new ShipCommand.Line();
        line.setLineNo(1);
        line.setQuantity(new BigDecimal("5"));
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造草稿订单。
     *
     * @return 订单
     */
    private SalesOrder draftOrder() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setDocNo("SO-001");
        order.setCustomerId(7201L);
        order.setCustomerName("客户A");
        order.setWarehouseId(7301L);
        order.setCurrency("CNY");
        order.setStatus(ScmStatus.DRAFT);
        SalesOrderLine line = new SalesOrderLine();
        line.setLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setQuantity(new BigDecimal("5"));
        line.setShippedQuantity(BigDecimal.ZERO);
        line.setUnitPrice(new BigDecimal("10"));
        order.setLines(List.of(line));
        order.calculateTotal();
        return order;
    }

    /**
     * 构造已审核订单。
     *
     * @return 订单
     */
    private SalesOrder approvedOrder() {
        SalesOrder order = draftOrder();
        order.approve();
        return order;
    }

    /**
     * 构造客户。
     *
     * @param creditLimit 信用额度
     * @return 客户
     */
    private Customer customer(String creditLimit) {
        Customer customer = new Customer();
        customer.setId(7201L);
        customer.setCode("CUS-001");
        customer.setName("客户A");
        customer.setCreditLimit(new BigDecimal(creditLimit));
        customer.setStatus(MasterDataStatus.ENABLED);
        return customer;
    }

    /**
     * 构造物料快照。
     *
     * @return 快照
     */
    private ItemSnapshot item() {
        ItemSnapshot snapshot = new ItemSnapshot();
        snapshot.setId(3L);
        snapshot.setCode("ERP-001");
        snapshot.setName("物料A");
        snapshot.setStatus(MasterDataStatus.ENABLED);
        return snapshot;
    }

    /**
     * 构造仓库。
     *
     * @return 仓库
     */
    private Warehouse warehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(7301L);
        warehouse.setCode("WH-001");
        warehouse.setName("主仓");
        warehouse.setStatus(MasterDataStatus.ENABLED);
        return warehouse;
    }
}

package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.PurchaseOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.ReceiveCommand;
import com.bluewhale.erp.scm.application.view.PurchaseOrderView;
import com.bluewhale.erp.scm.domain.model.PurchaseOrder;
import com.bluewhale.erp.scm.domain.model.PurchaseOrderLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.port.SupplierCatalog;
import com.bluewhale.erp.scm.domain.port.SupplierSnapshot;
import com.bluewhale.erp.scm.domain.repository.PurchaseOrderRepository;
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
 * {@link PurchaseAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class PurchaseAppServiceImplTest {

    private final PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);

    private final SupplierCatalog supplierCatalog = mock(SupplierCatalog.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);

    private final StockRepository stockRepository = mock(StockRepository.class);

    private final ArApAppService arApAppService = mock(ArApAppService.class);

    private final AutoVoucherAppService autoVoucherAppService = mock(AutoVoucherAppService.class);

    private final PurchaseAppServiceImpl service = new PurchaseAppServiceImpl(purchaseOrderRepository,
            supplierCatalog, itemCatalog, warehouseRepository, stockRepository, arApAppService,
            autoVoucherAppService);

    @Test
    @DisplayName("新增采购订单：快照供应商与物料，状态为草稿")
    void shouldCreatePurchaseOrder() {
        when(supplierCatalog.findById(9001L)).thenReturn(Optional.of(supplier()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(warehouseRepository.findById(7301L)).thenReturn(Optional.of(warehouse()));
        when(purchaseOrderRepository.nextDocNo(LocalDate.of(2026, 11, 4))).thenReturn("PO-20261104-0001");

        PurchaseOrderView view = service.create(command());

        assertEquals("PO-20261104-0001", view.getDocNo());
        assertEquals(ScmStatus.DRAFT, view.getStatus());
        assertEquals("供应商A", view.getSupplierName());
        assertEquals(new BigDecimal("20.00"), view.getTotalAmount());
        assertEquals("ERP-001", view.getLines().get(0).getItemCode());
        verify(purchaseOrderRepository).insert(any(PurchaseOrder.class));
    }

    @Test
    @DisplayName("停用供应商拒绝建单")
    void shouldRejectDisabledSupplier() {
        SupplierSnapshot supplier = supplier();
        supplier.setStatus(MasterDataStatus.DISABLED);
        when(supplierCatalog.findById(9001L)).thenReturn(Optional.of(supplier));

        BizException exception = assertThrows(BizException.class, () -> service.create(command()));

        assertEquals(ErpErrorCode.SUPPLIER_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("收货：入库、生成应付并自动记账")
    void shouldReceiveGoods() {
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(approvedOrder()));
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.empty());

        PurchaseOrderView view = service.receive(1L, receiveCommand(), 99L);

        assertEquals(ScmStatus.RECEIVED, view.getStatus());
        verify(stockRepository).insertMovement(any());
        verify(stockRepository).saveBalance(any(StockBalance.class));
        verify(arApAppService).createPayable(any());
        verify(autoVoucherAppService).postReceipt(any());
    }

    @Test
    @DisplayName("超收拒绝")
    void shouldRejectOverReceive() {
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(approvedOrder()));

        BizException exception = assertThrows(BizException.class, () -> service.receive(1L, overReceive(), 99L));

        assertEquals(ErpErrorCode.RECEIVE_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("分页查询返回总条数")
    void shouldPage() {
        when(purchaseOrderRepository.page(any())).thenReturn(List.of(approvedOrder()));
        when(purchaseOrderRepository.count(any())).thenReturn(1L);

        var page = service.page(new ScmQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    @Test
    @DisplayName("取消订单状态变更为已取消")
    void shouldCancel() {
        PurchaseOrder order = newOrder();
        when(purchaseOrderRepository.findById(2L)).thenReturn(Optional.of(order));

        PurchaseOrderView view = service.cancel(2L);

        assertEquals(ScmStatus.CANCELLED, view.getStatus());
        verify(purchaseOrderRepository).update(any(PurchaseOrder.class));
    }

    /**
     * 构造创建命令。
     *
     * @return 命令
     */
    private PurchaseOrderCreateCommand command() {
        PurchaseOrderCreateCommand command = new PurchaseOrderCreateCommand();
        command.setSupplierId(9001L);
        command.setDocDate(LocalDate.of(2026, 11, 4));
        command.setWarehouseId(7301L);
        PurchaseOrderCreateCommand.Line line = new PurchaseOrderCreateCommand.Line();
        line.setItemCode("ERP-001");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("10"));
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造收货命令。
     *
     * @return 命令
     */
    private ReceiveCommand receiveCommand() {
        ReceiveCommand command = new ReceiveCommand();
        command.setReceiveDate(LocalDate.of(2026, 11, 4));
        ReceiveCommand.Line line = new ReceiveCommand.Line();
        line.setLineNo(1);
        line.setQuantity(new BigDecimal("5"));
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造超量收货命令。
     *
     * @return 命令
     */
    private ReceiveCommand overReceive() {
        ReceiveCommand command = receiveCommand();
        command.getLines().get(0).setQuantity(new BigDecimal("6"));
        return command;
    }

    /**
     * 构造已审核订单。
     *
     * @return 订单
     */
    private PurchaseOrder approvedOrder() {
        PurchaseOrder order = newOrder();
        order.approve();
        return order;
    }

    /**
     * 构造草稿订单。
     *
     * @return 订单
     */
    private PurchaseOrder newOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setDocNo("PO-001");
        order.setSupplierId(9001L);
        order.setSupplierName("供应商A");
        order.setWarehouseId(7301L);
        order.setCurrency("CNY");
        order.setStatus(ScmStatus.DRAFT);
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setQuantity(new BigDecimal("5"));
        line.setReceivedQuantity(BigDecimal.ZERO);
        line.setUnitPrice(new BigDecimal("10"));
        order.setLines(List.of(line));
        order.calculateTotal();
        return order;
    }

    /**
     * 构造供应商快照。
     *
     * @return 快照
     */
    private SupplierSnapshot supplier() {
        SupplierSnapshot snapshot = new SupplierSnapshot();
        snapshot.setId(9001L);
        snapshot.setCode("SUP-001");
        snapshot.setName("供应商A");
        snapshot.setStatus(MasterDataStatus.ENABLED);
        return snapshot;
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

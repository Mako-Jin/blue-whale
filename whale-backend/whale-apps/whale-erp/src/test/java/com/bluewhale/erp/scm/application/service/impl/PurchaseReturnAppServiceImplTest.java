package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.scm.application.command.PurchaseReturnCreateCommand;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PurchaseReturnAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class PurchaseReturnAppServiceImplTest {

    private final PurchaseReturnRepository returnRepository = mock(PurchaseReturnRepository.class);

    private final PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);

    private final StockRepository stockRepository = mock(StockRepository.class);

    private final ArApAppService arApAppService = mock(ArApAppService.class);

    private final AutoVoucherAppService autoVoucherAppService = mock(AutoVoucherAppService.class);

    private final PurchaseReturnAppServiceImpl service = new PurchaseReturnAppServiceImpl(returnRepository,
            orderRepository, stockRepository, arApAppService, autoVoucherAppService);

    @Test
    @DisplayName("建单：引用已收货订单行，状态为草稿")
    void shouldCreateReturn() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order()));
        when(returnRepository.sumReturnedQuantity(1L, 1)).thenReturn(BigDecimal.ZERO);
        when(returnRepository.nextDocNo(LocalDate.of(2026, 11, 4))).thenReturn("PR-20261104-0001");

        PurchaseReturnView view = service.create(command(new BigDecimal("5")));

        assertEquals("PR-20261104-0001", view.getDocNo());
        assertEquals(ScmStatus.DRAFT, view.getStatus());
        assertEquals(0, view.getTotalAmount().compareTo(new BigDecimal("50")));
        assertEquals(7301L, view.getWarehouseId());
        verify(returnRepository).insert(any(PurchaseReturn.class));
    }

    @Test
    @DisplayName("建单：超过已收数量时拒绝")
    void shouldRejectOverQuantity() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order()));
        when(returnRepository.sumReturnedQuantity(1L, 1)).thenReturn(new BigDecimal("6"));

        PurchaseReturnCreateCommand command = command(new BigDecimal("5"));

        BizException exception = assertThrows(BizException.class, () -> service.create(command));

        assertEquals(ErpErrorCode.RETURN_QUANTITY_EXCEEDED, exception.getCode());
        verify(returnRepository, never()).insert(any(PurchaseReturn.class));
    }

    @Test
    @DisplayName("过账：库存出库、应付冲减并生成红字凭证")
    void shouldPostReturn() {
        PurchaseReturn purchaseReturn = draftReturn();
        when(returnRepository.findById(2L)).thenReturn(Optional.of(purchaseReturn));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order()));
        when(returnRepository.sumReturnedQuantity(1L, 1)).thenReturn(new BigDecimal("5"));
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.of(balance()));

        PurchaseReturnView view = service.post(2L, 9L);

        assertEquals(ScmStatus.POSTED, view.getStatus());
        assertEquals(0, purchaseReturn.getTotalAmount().compareTo(new BigDecimal("50")));
        assertEquals(0, purchaseReturn.getLines().get(0).getUnitCost().compareTo(new BigDecimal("10")));
        verify(stockRepository).insertMovement(any(StockMovement.class));
        verify(arApAppService).offsetPayable(eq(1L), any(BigDecimal.class));
        verify(autoVoucherAppService).postPurchaseReturn(any(AutoVoucherCommand.class));
        verify(returnRepository).update(any(PurchaseReturn.class));
    }

    @Test
    @DisplayName("过账：非草稿状态拒绝重复过账")
    void shouldRejectDuplicatePost() {
        PurchaseReturn purchaseReturn = draftReturn();
        purchaseReturn.setStatus(ScmStatus.POSTED);
        when(returnRepository.findById(2L)).thenReturn(Optional.of(purchaseReturn));

        BizException exception = assertThrows(BizException.class, () -> service.post(2L, 9L));

        assertEquals(ErpErrorCode.RETURN_STATE_CONFLICT, exception.getCode());
        verify(stockRepository, never()).insertMovement(any(StockMovement.class));
    }

    @Test
    @DisplayName("取消：草稿状态变更为已取消")
    void shouldCancel() {
        when(returnRepository.findById(2L)).thenReturn(Optional.of(draftReturn()));

        PurchaseReturnView view = service.cancel(2L);

        assertEquals(ScmStatus.CANCELLED, view.getStatus());
        verify(returnRepository).update(any(PurchaseReturn.class));
    }

    @Test
    @DisplayName("分页查询返回总条数")
    void shouldPage() {
        when(returnRepository.page(any())).thenReturn(List.of(draftReturn()));
        when(returnRepository.count(any())).thenReturn(1L);

        var page = service.page(new ScmQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    /**
     * 构造创建命令。
     *
     * @param quantity 退货数量
     * @return 命令
     */
    private PurchaseReturnCreateCommand command(BigDecimal quantity) {
        PurchaseReturnCreateCommand command = new PurchaseReturnCreateCommand();
        command.setPurchaseOrderId(1L);
        command.setDocDate(LocalDate.of(2026, 11, 4));
        PurchaseReturnCreateCommand.Line line = new PurchaseReturnCreateCommand.Line();
        line.setOrderLineNo(1);
        line.setQuantity(quantity);
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造草稿退货单。
     *
     * @return 退货单
     */
    private PurchaseReturn draftReturn() {
        PurchaseReturn purchaseReturn = new PurchaseReturn();
        purchaseReturn.setId(2L);
        purchaseReturn.setDocNo("PR-001");
        purchaseReturn.setPurchaseOrderId(1L);
        purchaseReturn.setSupplierId(1L);
        purchaseReturn.setSupplierName("供应商A");
        purchaseReturn.setDocDate(LocalDate.of(2026, 11, 4));
        purchaseReturn.setWarehouseId(7301L);
        purchaseReturn.setStatus(ScmStatus.DRAFT);
        purchaseReturn.setCurrency("CNY");
        PurchaseReturnLine line = new PurchaseReturnLine();
        line.setLineNo(1);
        line.setOrderLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setUom("个");
        line.setQuantity(new BigDecimal("5"));
        line.setUnitPrice(new BigDecimal("10"));
        purchaseReturn.setLines(List.of(line));
        purchaseReturn.calculateTotal();
        return purchaseReturn;
    }

    /**
     * 构造已收货采购订单。
     *
     * @return 订单
     */
    private PurchaseOrder order() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setSupplierId(1L);
        order.setSupplierName("供应商A");
        order.setWarehouseId(7301L);
        order.setCurrency("CNY");
        order.setStatus(ScmStatus.RECEIVED);
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setUom("个");
        line.setQuantity(new BigDecimal("20"));
        line.setUnitPrice(new BigDecimal("10"));
        line.setReceivedQuantity(new BigDecimal("10"));
        order.setLines(List.of(line));
        return order;
    }

    /**
     * 构造库存余额（10 件 / 100 元）。
     *
     * @return 余额
     */
    private StockBalance balance() {
        StockBalance balance = new StockBalance();
        balance.setId(7L);
        balance.setItemId(3L);
        balance.setItemCode("ERP-001");
        balance.setWarehouseId(7301L);
        balance.setQuantity(new BigDecimal("10"));
        balance.setAmount(new BigDecimal("100"));
        return balance;
    }
}

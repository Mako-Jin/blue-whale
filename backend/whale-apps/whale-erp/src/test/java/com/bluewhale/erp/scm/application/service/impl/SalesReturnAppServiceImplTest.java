package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.scm.application.command.SalesReturnCreateCommand;
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
 * {@link SalesReturnAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class SalesReturnAppServiceImplTest {

    private final SalesReturnRepository returnRepository = mock(SalesReturnRepository.class);

    private final SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);

    private final StockRepository stockRepository = mock(StockRepository.class);

    private final ArApAppService arApAppService = mock(ArApAppService.class);

    private final AutoVoucherAppService autoVoucherAppService = mock(AutoVoucherAppService.class);

    private final SalesReturnAppServiceImpl service = new SalesReturnAppServiceImpl(returnRepository,
            orderRepository, stockRepository, arApAppService, autoVoucherAppService);

    @Test
    @DisplayName("建单：引用已发货订单行，状态为草稿")
    void shouldCreateReturn() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order()));
        when(returnRepository.sumReturnedQuantity(1L, 1)).thenReturn(BigDecimal.ZERO);
        when(returnRepository.nextDocNo(LocalDate.of(2026, 11, 4))).thenReturn("SR-20261104-0001");

        SalesReturnView view = service.create(command(new BigDecimal("5")));

        assertEquals("SR-20261104-0001", view.getDocNo());
        assertEquals(ScmStatus.DRAFT, view.getStatus());
        assertEquals(0, view.getTotalAmount().compareTo(new BigDecimal("100")));
        verify(returnRepository).insert(any(SalesReturn.class));
    }

    @Test
    @DisplayName("建单：超过已发数量时拒绝")
    void shouldRejectOverQuantity() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order()));
        when(returnRepository.sumReturnedQuantity(1L, 1)).thenReturn(new BigDecimal("6"));

        SalesReturnCreateCommand command = command(new BigDecimal("5"));

        BizException exception = assertThrows(BizException.class, () -> service.create(command));

        assertEquals(ErpErrorCode.RETURN_QUANTITY_EXCEEDED, exception.getCode());
        verify(returnRepository, never()).insert(any(SalesReturn.class));
    }

    @Test
    @DisplayName("过账：按当前均价入库、应收冲减并生成带成本的红字凭证")
    void shouldPostReturn() {
        SalesReturn salesReturn = draftReturn();
        when(returnRepository.findById(2L)).thenReturn(Optional.of(salesReturn));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order()));
        when(returnRepository.sumReturnedQuantity(1L, 1)).thenReturn(new BigDecimal("5"));
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.of(balance()));

        SalesReturnView view = service.post(2L, 9L);

        assertEquals(ScmStatus.POSTED, view.getStatus());
        assertEquals(0, salesReturn.getTotalAmount().compareTo(new BigDecimal("100")));
        assertEquals(0, salesReturn.getLines().get(0).getUnitCost().compareTo(new BigDecimal("10")));
        assertEquals(0, salesReturn.getLines().get(0).getCostAmount().compareTo(new BigDecimal("50")));
        verify(stockRepository).insertMovement(any(StockMovement.class));
        verify(arApAppService).offsetReceivable(eq(1L), any(BigDecimal.class));
        verify(autoVoucherAppService).postSalesReturn(any(AutoVoucherCommand.class));
        verify(returnRepository).update(any(SalesReturn.class));
    }

    @Test
    @DisplayName("过账：非草稿状态拒绝重复过账")
    void shouldRejectDuplicatePost() {
        SalesReturn salesReturn = draftReturn();
        salesReturn.setStatus(ScmStatus.POSTED);
        when(returnRepository.findById(2L)).thenReturn(Optional.of(salesReturn));

        BizException exception = assertThrows(BizException.class, () -> service.post(2L, 9L));

        assertEquals(ErpErrorCode.RETURN_STATE_CONFLICT, exception.getCode());
        verify(stockRepository, never()).insertMovement(any(StockMovement.class));
    }

    @Test
    @DisplayName("取消：草稿状态变更为已取消")
    void shouldCancel() {
        when(returnRepository.findById(2L)).thenReturn(Optional.of(draftReturn()));

        SalesReturnView view = service.cancel(2L);

        assertEquals(ScmStatus.CANCELLED, view.getStatus());
        verify(returnRepository).update(any(SalesReturn.class));
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
    private SalesReturnCreateCommand command(BigDecimal quantity) {
        SalesReturnCreateCommand command = new SalesReturnCreateCommand();
        command.setSalesOrderId(1L);
        command.setDocDate(LocalDate.of(2026, 11, 4));
        SalesReturnCreateCommand.Line line = new SalesReturnCreateCommand.Line();
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
    private SalesReturn draftReturn() {
        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setId(2L);
        salesReturn.setDocNo("SR-001");
        salesReturn.setSalesOrderId(1L);
        salesReturn.setCustomerId(1L);
        salesReturn.setCustomerName("客户A");
        salesReturn.setDocDate(LocalDate.of(2026, 11, 4));
        salesReturn.setWarehouseId(7301L);
        salesReturn.setStatus(ScmStatus.DRAFT);
        salesReturn.setCurrency("CNY");
        SalesReturnLine line = new SalesReturnLine();
        line.setLineNo(1);
        line.setOrderLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setUom("个");
        line.setQuantity(new BigDecimal("5"));
        line.setUnitPrice(new BigDecimal("20"));
        salesReturn.setLines(List.of(line));
        salesReturn.calculateTotal();
        return salesReturn;
    }

    /**
     * 构造已发货销售订单。
     *
     * @return 订单
     */
    private SalesOrder order() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setCustomerId(1L);
        order.setCustomerName("客户A");
        order.setWarehouseId(7301L);
        order.setCurrency("CNY");
        order.setStatus(ScmStatus.SHIPPED);
        SalesOrderLine line = new SalesOrderLine();
        line.setLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setUom("个");
        line.setQuantity(new BigDecimal("20"));
        line.setUnitPrice(new BigDecimal("20"));
        line.setShippedQuantity(new BigDecimal("10"));
        order.setLines(List.of(line));
        return order;
    }

    /**
     * 构造库存余额（10 件 / 100 元，均价 10 元）。
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

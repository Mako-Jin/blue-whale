package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.TransferCreateCommand;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TransferAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class TransferAppServiceImplTest {

    private final TransferRepository transferRepository = mock(TransferRepository.class);

    private final StockRepository stockRepository = mock(StockRepository.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);

    private final TransferAppServiceImpl service = new TransferAppServiceImpl(transferRepository,
            stockRepository, itemCatalog, warehouseRepository);

    @Test
    @DisplayName("新增调拨单：快照物料与仓库，状态为草稿")
    void shouldCreateTransfer() {
        when(warehouseRepository.findById(7301L)).thenReturn(Optional.of(warehouse(7301L, "主仓")));
        when(warehouseRepository.findById(7302L)).thenReturn(Optional.of(warehouse(7302L, "分仓")));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(transferRepository.nextDocNo(LocalDate.of(2026, 11, 4))).thenReturn("TR-20261104-0001");

        TransferView view = service.create(command());

        assertEquals("TR-20261104-0001", view.getDocNo());
        assertEquals(ScmStatus.DRAFT, view.getStatus());
        assertEquals("主仓", view.getFromWarehouseName());
        assertEquals("分仓", view.getToWarehouseName());
        assertEquals(0, view.getTotalQuantity().compareTo(new BigDecimal("5")));
        verify(transferRepository).insert(any(Transfer.class));
    }

    @Test
    @DisplayName("调出与调入仓库相同时拒绝建单")
    void shouldRejectSameWarehouse() {
        when(warehouseRepository.findById(7301L)).thenReturn(Optional.of(warehouse(7301L, "主仓")));
        TransferCreateCommand command = command();
        command.setToWarehouseId(7301L);

        BizException exception = assertThrows(BizException.class, () -> service.create(command));

        assertEquals(ErpErrorCode.TRANSFER_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("过账：调出仓出库、调入仓按同成本入库")
    void shouldPostTransfer() {
        Transfer transfer = draftTransfer();
        when(transferRepository.findById(1L)).thenReturn(Optional.of(transfer));
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.of(balance(10, "50")));
        when(stockRepository.findBalance(3L, 7302L)).thenReturn(Optional.empty());

        TransferView view = service.post(1L);

        assertEquals(ScmStatus.POSTED, view.getStatus());
        assertEquals(0, transfer.getTotalAmount().compareTo(new BigDecimal("25")));
        assertEquals(0, transfer.getLines().get(0).getUnitCost().compareTo(new BigDecimal("5")));
        verify(stockRepository, times(2)).insertMovement(any(StockMovement.class));
        verify(stockRepository, times(2)).saveBalance(any(StockBalance.class));
        verify(transferRepository).update(any(Transfer.class));
    }

    @Test
    @DisplayName("调出仓库存不足时拒绝过账")
    void shouldRejectInsufficientStock() {
        Transfer transfer = draftTransfer();
        when(transferRepository.findById(1L)).thenReturn(Optional.of(transfer));
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.of(balance(2, "10")));

        BizException exception = assertThrows(BizException.class, () -> service.post(1L));

        assertEquals(ErpErrorCode.NEGATIVE_STOCK, exception.getCode());
        verify(transferRepository, never()).update(any(Transfer.class));
    }

    @Test
    @DisplayName("分页查询返回总条数")
    void shouldPage() {
        when(transferRepository.page(any())).thenReturn(List.of(draftTransfer()));
        when(transferRepository.count(any())).thenReturn(1L);

        var page = service.page(new ScmQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    @Test
    @DisplayName("取消调拨单状态变更为已取消")
    void shouldCancel() {
        Transfer transfer = draftTransfer();
        when(transferRepository.findById(1L)).thenReturn(Optional.of(transfer));

        TransferView view = service.cancel(1L);

        assertEquals(ScmStatus.CANCELLED, view.getStatus());
        verify(transferRepository).update(any(Transfer.class));
    }

    /**
     * 构造创建命令。
     *
     * @return 命令
     */
    private TransferCreateCommand command() {
        TransferCreateCommand command = new TransferCreateCommand();
        command.setTransferDate(LocalDate.of(2026, 11, 4));
        command.setFromWarehouseId(7301L);
        command.setToWarehouseId(7302L);
        TransferCreateCommand.Line line = new TransferCreateCommand.Line();
        line.setItemCode("ERP-001");
        line.setQuantity(new BigDecimal("5"));
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造草稿调拨单。
     *
     * @return 调拨单
     */
    private Transfer draftTransfer() {
        Transfer transfer = new Transfer();
        transfer.setId(1L);
        transfer.setDocNo("TR-001");
        transfer.setTransferDate(LocalDate.of(2026, 11, 4));
        transfer.setFromWarehouseId(7301L);
        transfer.setFromWarehouseName("主仓");
        transfer.setToWarehouseId(7302L);
        transfer.setToWarehouseName("分仓");
        transfer.setStatus(ScmStatus.DRAFT);
        TransferLine line = new TransferLine();
        line.setLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setQuantity(new BigDecimal("5"));
        line.setUnitCost(BigDecimal.ZERO);
        line.setAmount(BigDecimal.ZERO);
        transfer.setLines(List.of(line));
        transfer.calculateTotal();
        return transfer;
    }

    /**
     * 构造库存余额。
     *
     * @param quantity 数量
     * @param amount 金额
     * @return 余额
     */
    private StockBalance balance(int quantity, String amount) {
        StockBalance balance = new StockBalance();
        balance.setId(7L);
        balance.setItemId(3L);
        balance.setItemCode("ERP-001");
        balance.setWarehouseId(7301L);
        balance.setQuantity(new BigDecimal(quantity));
        balance.setAmount(new BigDecimal(amount));
        return balance;
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
     * @param id 主键
     * @param name 名称
     * @return 仓库
     */
    private Warehouse warehouse(Long id, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setCode("WH-" + id);
        warehouse.setName(name);
        warehouse.setStatus(MasterDataStatus.ENABLED);
        return warehouse;
    }
}

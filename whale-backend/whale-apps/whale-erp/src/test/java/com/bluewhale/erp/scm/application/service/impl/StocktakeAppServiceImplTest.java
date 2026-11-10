package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.StocktakeCreateCommand;
import com.bluewhale.erp.scm.application.view.StocktakeView;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.model.Stocktake;
import com.bluewhale.erp.scm.domain.model.StocktakeLine;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import com.bluewhale.erp.scm.domain.repository.StocktakeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StocktakeAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class StocktakeAppServiceImplTest {

    private final StocktakeRepository stocktakeRepository = mock(StocktakeRepository.class);

    private final StockRepository stockRepository = mock(StockRepository.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);

    private final AutoVoucherAppService autoVoucherAppService = mock(AutoVoucherAppService.class);

    private final StocktakeAppServiceImpl service = new StocktakeAppServiceImpl(stocktakeRepository,
            stockRepository, itemCatalog, warehouseRepository, autoVoucherAppService);

    @Test
    @DisplayName("建档快照账面数量与成本并计算差异")
    void shouldCreateStocktakeSnapshot() {
        when(warehouseRepository.findById(7301L)).thenReturn(Optional.of(warehouse()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.of(balance("10", "50")));
        when(stocktakeRepository.nextDocNo(LocalDate.of(2026, 11, 4))).thenReturn("ST-20261104-0001");

        StocktakeView view = service.create(command(new BigDecimal("8")));

        assertEquals("ST-20261104-0001", view.getDocNo());
        assertEquals(ScmStatus.DRAFT, view.getStatus());
        assertEquals(0, view.getLines().get(0).getBookQuantity().compareTo(new BigDecimal("10")));
        assertEquals(0, view.getLines().get(0).getDifference().compareTo(new BigDecimal("-2")));
        assertEquals(0, view.getLines().get(0).getAmount().compareTo(new BigDecimal("-10")));
        assertEquals(0, view.getLossAmount().compareTo(new BigDecimal("10")));
        verify(stocktakeRepository).insert(any(Stocktake.class));
    }

    @Test
    @DisplayName("物料不存在时拒绝建档")
    void shouldRejectUnknownItem() {
        when(warehouseRepository.findById(7301L)).thenReturn(Optional.of(warehouse()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.create(command(new BigDecimal("8"))));

        assertEquals(ErpErrorCode.ITEM_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("盘亏过账：出库并生成借方待处理财产损溢凭证")
    void shouldPostLoss() {
        Stocktake stocktake = draftStocktake(new BigDecimal("10"), new BigDecimal("8"));
        when(stocktakeRepository.findById(1L)).thenReturn(Optional.of(stocktake));
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.of(balance("10", "50")));

        StocktakeView view = service.post(1L, 99L);

        assertEquals(ScmStatus.POSTED, view.getStatus());
        assertEquals(0, view.getLossAmount().compareTo(new BigDecimal("10")));
        verify(stockRepository).insertMovement(any(StockMovement.class));
        verify(stockRepository).saveBalance(any(StockBalance.class));
        ArgumentCaptor<AutoVoucherCommand> captor = ArgumentCaptor.forClass(AutoVoucherCommand.class);
        verify(autoVoucherAppService).postStockVariance(captor.capture());
        assertEquals(0, captor.getValue().getAmount().compareTo(new BigDecimal("-10")));
        assertEquals("STOCKTAKE", captor.getValue().getSourceType());
    }

    @Test
    @DisplayName("盘盈过账：无余额时新建并入账")
    void shouldPostGain() {
        Stocktake stocktake = draftStocktake(new BigDecimal("10"), new BigDecimal("12"));
        when(stocktakeRepository.findById(1L)).thenReturn(Optional.of(stocktake));
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.empty());

        StocktakeView view = service.post(1L, 99L);

        assertEquals(0, view.getGainAmount().compareTo(new BigDecimal("10")));
        verify(stockRepository).insertMovement(any(StockMovement.class));
        verify(stockRepository).saveBalance(any(StockBalance.class));
        ArgumentCaptor<AutoVoucherCommand> captor = ArgumentCaptor.forClass(AutoVoucherCommand.class);
        verify(autoVoucherAppService).postStockVariance(captor.capture());
        assertEquals(0, captor.getValue().getAmount().compareTo(new BigDecimal("10")));
    }

    @Test
    @DisplayName("无差异时过账不生成流水与凭证")
    void shouldSkipVoucherWithoutDifference() {
        Stocktake stocktake = draftStocktake(new BigDecimal("10"), new BigDecimal("10"));
        when(stocktakeRepository.findById(1L)).thenReturn(Optional.of(stocktake));

        StocktakeView view = service.post(1L, 99L);

        assertEquals(ScmStatus.POSTED, view.getStatus());
        verify(stockRepository, never()).insertMovement(any(StockMovement.class));
        verify(autoVoucherAppService, never()).postStockVariance(any(AutoVoucherCommand.class));
    }

    @Test
    @DisplayName("已过账盘点单不可取消")
    void shouldRejectCancelAfterPost() {
        Stocktake stocktake = draftStocktake(new BigDecimal("10"), new BigDecimal("10"));
        stocktake.post();
        when(stocktakeRepository.findById(1L)).thenReturn(Optional.of(stocktake));

        BizException exception = assertThrows(BizException.class, () -> service.cancel(1L));

        assertEquals(ErpErrorCode.STOCKTAKE_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造创建命令。
     *
     * @param counted 实盘数量
     * @return 命令
     */
    private StocktakeCreateCommand command(BigDecimal counted) {
        StocktakeCreateCommand command = new StocktakeCreateCommand();
        command.setStocktakeDate(LocalDate.of(2026, 11, 4));
        command.setWarehouseId(7301L);
        StocktakeCreateCommand.Line line = new StocktakeCreateCommand.Line();
        line.setItemCode("ERP-001");
        line.setCountedQuantity(counted);
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造草稿盘点单。
     *
     * @param book 账面数量
     * @param counted 实盘数量
     * @return 盘点单
     */
    private Stocktake draftStocktake(BigDecimal book, BigDecimal counted) {
        Stocktake stocktake = new Stocktake();
        stocktake.setId(1L);
        stocktake.setDocNo("ST-001");
        stocktake.setStocktakeDate(LocalDate.of(2026, 11, 4));
        stocktake.setWarehouseId(7301L);
        stocktake.setWarehouseName("主仓");
        stocktake.setStatus(ScmStatus.DRAFT);
        StocktakeLine line = new StocktakeLine();
        line.setLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setBookQuantity(book);
        line.setCountedQuantity(counted);
        line.calculate(new BigDecimal("5"));
        stocktake.setLines(List.of(line));
        stocktake.refreshTotals();
        return stocktake;
    }

    /**
     * 构造库存余额。
     *
     * @param quantity 数量
     * @param amount 金额
     * @return 余额
     */
    private StockBalance balance(String quantity, String amount) {
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

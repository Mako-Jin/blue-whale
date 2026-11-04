package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.StockAdjustCommand;
import com.bluewhale.erp.scm.application.view.StockMovementView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InventoryAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class InventoryAppServiceImplTest {

    private final StockRepository stockRepository = mock(StockRepository.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);

    private final InventoryAppServiceImpl service = new InventoryAppServiceImpl(stockRepository, itemCatalog,
            warehouseRepository);

    @Test
    @DisplayName("盘盈按当前平均成本入库")
    void shouldAdjustInbound() {
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(warehouseRepository.findById(7301L)).thenReturn(Optional.of(warehouse()));
        StockBalance balance = balance("10", "100");
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.of(balance));

        StockMovementView view = service.adjust(adjust("2"), 99L);

        assertEquals(ScmStatus.IN, view.getDirection());
        assertEquals(0, balance.getQuantity().compareTo(new BigDecimal("12")));
        verify(stockRepository).saveBalance(balance);
        verify(stockRepository).insertMovement(any(StockMovement.class));
    }

    @Test
    @DisplayName("盘亏按移动加权成本出库")
    void shouldAdjustOutbound() {
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(warehouseRepository.findById(7301L)).thenReturn(Optional.of(warehouse()));
        StockBalance balance = balance("10", "100");
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.of(balance));

        StockMovementView view = service.adjust(adjust("-2"), 99L);

        assertEquals(ScmStatus.OUT, view.getDirection());
        assertEquals(0, view.getAmount().compareTo(new BigDecimal("20")));
        assertEquals(0, balance.getQuantity().compareTo(new BigDecimal("8")));
    }

    @Test
    @DisplayName("库存不足时盘亏拒绝")
    void shouldRejectNegativeStock() {
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(warehouseRepository.findById(7301L)).thenReturn(Optional.of(warehouse()));
        StockBalance balance = balance("10", "100");
        when(stockRepository.findBalance(3L, 7301L)).thenReturn(Optional.of(balance));

        BizException exception = assertThrows(BizException.class, () -> service.adjust(adjust("-11"), 99L));

        assertEquals(ErpErrorCode.NEGATIVE_STOCK, exception.getCode());
    }

    @Test
    @DisplayName("调整数量为零时拒绝")
    void shouldRejectZeroQuantity() {
        BizException exception = assertThrows(BizException.class, () -> service.adjust(adjust("0"), 99L));

        assertEquals(ErpErrorCode.QUANTITY_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("库存余额与流水分页映射")
    void shouldPageBalancesAndMovements() {
        StockBalance balance = balance("10", "100");
        balance.setItemCode("ERP-001");
        StockMovement movement = new StockMovement();
        movement.setDocNo("ADJ-001");
        when(stockRepository.pageBalances(any())).thenReturn(List.of(balance));
        when(stockRepository.countBalances(any())).thenReturn(1L);
        when(stockRepository.pageMovements(any())).thenReturn(List.of(movement));
        when(stockRepository.countMovements(any())).thenReturn(1L);

        var balancePage = service.balancePage(new ScmQuery());
        var movementPage = service.movementPage(new ScmQuery());

        assertEquals(1, balancePage.getRecords().size());
        assertEquals("ERP-001", balancePage.getRecords().get(0).getItemCode());
        assertEquals(1, movementPage.getRecords().size());
        assertEquals("ADJ-001", movementPage.getRecords().get(0).getDocNo());
    }

    /**
     * 构造调整命令。
     *
     * @param quantity 调整数量
     * @return 命令
     */
    private StockAdjustCommand adjust(String quantity) {
        StockAdjustCommand command = new StockAdjustCommand();
        command.setItemCode("ERP-001");
        command.setWarehouseId(7301L);
        command.setQuantity(new BigDecimal(quantity));
        command.setRemark("盘点调整");
        return command;
    }

    /**
     * 构造余额。
     *
     * @param quantity 数量
     * @param amount 金额
     * @return 余额
     */
    private StockBalance balance(String quantity, String amount) {
        StockBalance balance = new StockBalance();
        balance.setItemId(3L);
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

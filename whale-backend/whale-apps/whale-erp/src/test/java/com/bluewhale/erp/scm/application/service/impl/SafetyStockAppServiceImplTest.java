package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.SafetyStockCreateCommand;
import com.bluewhale.erp.scm.application.command.SafetyStockUpdateCommand;
import com.bluewhale.erp.scm.application.view.IdleStockView;
import com.bluewhale.erp.scm.application.view.SafetyStockView;
import com.bluewhale.erp.scm.application.view.ShortageView;
import com.bluewhale.erp.scm.domain.model.SafetyStock;
import com.bluewhale.erp.scm.domain.model.SafetyStockQuery;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.SafetyStockRepository;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SafetyStockAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class SafetyStockAppServiceImplTest {

    private final SafetyStockRepository safetyStockRepository = mock(SafetyStockRepository.class);

    private final StockRepository stockRepository = mock(StockRepository.class);

    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final SafetyStockAppServiceImpl service = new SafetyStockAppServiceImpl(safetyStockRepository,
            stockRepository, warehouseRepository, itemCatalog);

    @Test
    @DisplayName("新增：快照仓库与物料并持久化")
    void shouldCreate() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(safetyStockRepository.find(1L, 2L)).thenReturn(Optional.empty());

        SafetyStockView view = service.create(createCommand());

        assertEquals(1L, view.getWarehouseId());
        assertEquals("ERP-001", view.getItemCode());
        assertEquals(0, view.getSafetyQty().compareTo(new BigDecimal("10")));
        verify(safetyStockRepository).insert(any(SafetyStock.class));
    }

    @Test
    @DisplayName("新增：同仓库同物料重复配置拒绝")
    void shouldRejectDuplicate() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(safetyStockRepository.find(1L, 2L)).thenReturn(Optional.of(safetyStock()));

        BizException exception = assertThrows(BizException.class, () -> service.create(createCommand()));

        assertEquals(ErpErrorCode.SAFETY_STOCK_CONFLICT, exception.getCode());
        verify(safetyStockRepository, never()).insert(any(SafetyStock.class));
    }

    @Test
    @DisplayName("更新：最高库存低于安全量拒绝")
    void shouldRejectInvalidUpdate() {
        when(safetyStockRepository.findById(5L)).thenReturn(Optional.of(safetyStock()));

        SafetyStockUpdateCommand command = new SafetyStockUpdateCommand();
        command.setSafetyQty(new BigDecimal("20"));
        command.setMaxQty(new BigDecimal("10"));

        BizException exception = assertThrows(BizException.class, () -> service.update(5L, command));

        assertEquals(ErpErrorCode.SAFETY_STOCK_INVALID, exception.getCode());
        verify(safetyStockRepository, never()).update(any(SafetyStock.class));
    }

    @Test
    @DisplayName("删除：不存在抛业务异常")
    void shouldRejectMissingDelete() {
        when(safetyStockRepository.findById(9L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.delete(9L));

        assertEquals(ErpErrorCode.SAFETY_STOCK_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("缺料预警：仅返回缺口大于 0 的项，余额缺失按 0")
    void shouldComputeShortage() {
        when(safetyStockRepository.list(any())).thenReturn(List.of(
                safetyStock(5L, 2L, "10"),
                safetyStock(6L, 3L, "10"),
                safetyStock(7L, 4L, "5")));
        when(stockRepository.findBalance(2L, 1L)).thenReturn(Optional.of(balance("4")));
        when(stockRepository.findBalance(3L, 1L)).thenReturn(Optional.of(balance("10")));
        when(stockRepository.findBalance(4L, 1L)).thenReturn(Optional.empty());

        var page = service.shortage(new SafetyStockQuery());

        assertEquals(2, page.getRecords().size());
        ShortageView first = page.getRecords().get(0);
        assertEquals(2L, first.getItemId());
        assertEquals(0, first.getOnHandQty().compareTo(new BigDecimal("4")));
        assertEquals(0, first.getShortageQty().compareTo(new BigDecimal("6")));
        ShortageView second = page.getRecords().get(1);
        assertEquals(4L, second.getItemId());
        assertEquals(0, second.getOnHandQty().signum());
        assertEquals(0, second.getShortageQty().compareTo(new BigDecimal("5")));
    }

    @Test
    @DisplayName("呆滞分析：按阈值过滤，从未出库计入且库龄为空")
    void shouldComputeIdle() {
        when(stockRepository.listBalances(any())).thenReturn(List.of(
                balance(2L, "100"),
                balance(3L, "20"),
                balance(4L, "0")));
        when(stockRepository.lastOutboundAt(2L, 1L))
                .thenReturn(Optional.of(LocalDateTime.now().minusDays(200)));
        when(stockRepository.lastOutboundAt(3L, 1L))
                .thenReturn(Optional.empty());

        var page = service.idle(new ScmQuery(), 90);

        assertEquals(2, page.getRecords().size());
        IdleStockView never = page.getRecords().get(0);
        assertEquals(3L, never.getItemId());
        assertNull(never.getIdleDays());
        assertNull(never.getLastOutboundAt());
        IdleStockView aged = page.getRecords().get(1);
        assertEquals(2L, aged.getItemId());
        assertEquals(200, aged.getIdleDays());
    }

    @Test
    @DisplayName("呆滞分析：阈值小于 1 抛业务异常")
    void shouldRejectInvalidIdleDays() {
        BizException exception = assertThrows(BizException.class,
                () -> service.idle(new ScmQuery(), 0));

        assertEquals(ErpErrorCode.SAFETY_STOCK_INVALID, exception.getCode());
        verify(stockRepository, never()).listBalances(any());
    }

    @Test
    @DisplayName("分页查询返回总条数")
    void shouldPage() {
        when(safetyStockRepository.page(any())).thenReturn(List.of(safetyStock()));
        when(safetyStockRepository.count(any())).thenReturn(1L);

        var page = service.page(new SafetyStockQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    /**
     * 构造创建命令。
     *
     * @return 命令
     */
    private SafetyStockCreateCommand createCommand() {
        SafetyStockCreateCommand command = new SafetyStockCreateCommand();
        command.setWarehouseId(1L);
        command.setItemCode("ERP-001");
        command.setSafetyQty(new BigDecimal("10"));
        return command;
    }

    /**
     * 构造安全库存。
     *
     * @return 安全库存
     */
    private SafetyStock safetyStock() {
        return safetyStock(5L, 2L, "10");
    }

    /**
     * 构造安全库存。
     *
     * @param id 主键
     * @param itemId 物料主键
     * @param safetyQty 安全库存量
     * @return 安全库存
     */
    private SafetyStock safetyStock(Long id, Long itemId, String safetyQty) {
        SafetyStock safetyStock = new SafetyStock();
        safetyStock.setId(id);
        safetyStock.setWarehouseId(1L);
        safetyStock.setItemId(itemId);
        safetyStock.setItemCode("ERP-00" + itemId);
        safetyStock.setItemName("物料" + itemId);
        safetyStock.setSafetyQty(new BigDecimal(safetyQty));
        return safetyStock;
    }

    /**
     * 构造库存余额。
     *
     * @param quantity 数量
     * @return 余额
     */
    private StockBalance balance(String quantity) {
        return balance(2L, quantity);
    }

    /**
     * 构造库存余额。
     *
     * @param itemId 物料主键
     * @param quantity 数量
     * @return 余额
     */
    private StockBalance balance(Long itemId, String quantity) {
        StockBalance balance = new StockBalance();
        balance.setItemId(itemId);
        balance.setItemCode("ERP-00" + itemId);
        balance.setItemName("物料" + itemId);
        balance.setWarehouseId(1L);
        balance.setQuantity(new BigDecimal(quantity));
        balance.setAmount(new BigDecimal("100"));
        return balance;
    }

    /**
     * 构造仓库。
     *
     * @return 仓库
     */
    private Warehouse warehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setCode("WH-001");
        warehouse.setName("主仓");
        warehouse.setStatus(MasterDataStatus.ENABLED);
        return warehouse;
    }

    /**
     * 构造物料快照。
     *
     * @return 快照
     */
    private ItemSnapshot item() {
        ItemSnapshot item = new ItemSnapshot();
        item.setId(2L);
        item.setCode("ERP-001");
        item.setName("物料A");
        item.setStatus(MasterDataStatus.ENABLED);
        return item;
    }
}

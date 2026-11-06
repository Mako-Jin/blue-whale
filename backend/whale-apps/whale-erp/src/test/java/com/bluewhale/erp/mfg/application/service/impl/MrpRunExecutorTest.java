package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.mfg.domain.model.BomLine;
import com.bluewhale.erp.mfg.domain.model.BomSnapshot;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.model.MrpPlannedOrder;
import com.bluewhale.erp.mfg.domain.model.MrpRun;
import com.bluewhale.erp.mfg.domain.model.MrpRunLine;
import com.bluewhale.erp.mfg.domain.model.MrpTrace;
import com.bluewhale.erp.mfg.domain.port.BomCatalog;
import com.bluewhale.erp.mfg.domain.port.StockAvailability;
import com.bluewhale.erp.mfg.domain.repository.MrpRepository;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MrpRunExecutor} 单元测试。
 *
 * @author BlueWhale
 */
class MrpRunExecutorTest {

    private final BomCatalog bomCatalog = mock(BomCatalog.class);

    private final StockAvailability stockAvailability = mock(StockAvailability.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final MrpRepository mrpRepository = mock(MrpRepository.class);

    private final MrpRunExecutor executor = new MrpRunExecutor(
            bomCatalog, stockAvailability, itemCatalog, mrpRepository);

    @Test
    @DisplayName("净需求与建议类型：生产子件、采购子件、库存覆盖跳过")
    void shouldComputeNetDemandAndOrderType() {
        stubSnapshot("BOM-P1", "V1");
        stubItems();
        when(stockAvailability.availableQuantity(11L)).thenReturn(new BigDecimal("5"));
        when(stockAvailability.availableQuantity(12L)).thenReturn(new BigDecimal("100"));
        when(stockAvailability.availableQuantity(13L)).thenReturn(BigDecimal.ZERO);
        assignOrderIds();
        MrpRun run = run(10);

        executor.execute(run);

        ArgumentCaptor<List<MrpPlannedOrder>> ordersCaptor = orderCaptor();
        verify(mrpRepository).insertPlannedOrders(ordersCaptor.capture());
        List<MrpPlannedOrder> orders = ordersCaptor.getValue();
        assertEquals(2, orders.size());
        MrpPlannedOrder subAssembly = orders.get(0);
        assertEquals("SA", subAssembly.getItemCode());
        assertEquals(MfgStatus.ORDER_PRODUCTION, subAssembly.getOrderType());
        assertEquals(0, subAssembly.getGrossQuantity().compareTo(new BigDecimal("20")));
        assertEquals(0, subAssembly.getNetQuantity().compareTo(new BigDecimal("15")));
        assertEquals(1, subAssembly.getItemLevel());
        MrpPlannedOrder component = orders.get(1);
        assertEquals("C2", component.getItemCode());
        assertEquals(MfgStatus.ORDER_PURCHASE, component.getOrderType());
        assertEquals(0, component.getNetQuantity().compareTo(new BigDecimal("50")));
        assertEquals(Integer.valueOf(3), run.getSnapshotLineCount());
        assertEquals(Integer.valueOf(2), run.getPlannedOrderCount());
        assertEquals(MfgStatus.COMPLETED, run.getStatus());
        verify(mrpRepository).insertTraces(any());
    }

    @Test
    @DisplayName("同一物料跨需求行：可用量只扣一次")
    void shouldDeductAvailableQuantityOnce() {
        stubSnapshot("BOM-P1", "V1");
        stubItems();
        when(stockAvailability.availableQuantity(11L)).thenReturn(BigDecimal.ZERO);
        when(stockAvailability.availableQuantity(12L)).thenReturn(BigDecimal.ZERO);
        when(stockAvailability.availableQuantity(13L)).thenReturn(BigDecimal.ZERO);
        MrpRun run = run(10);
        MrpRunLine second = new MrpRunLine();
        second.setLineNo(2);
        second.setItemCode("P1");
        second.setBomNo("BOM-P1");
        second.setBomVersion("V1");
        second.setQuantity(new BigDecimal("10"));
        second.setDueDate(LocalDate.of(2026, 11, 20));
        run.setLines(List.of(run.getLines().get(0), second));

        executor.execute(run);

        verify(stockAvailability, times(1)).availableQuantity(11L);
        verify(stockAvailability, times(1)).availableQuantity(12L);
        verify(stockAvailability, times(1)).availableQuantity(13L);
        ArgumentCaptor<List<MrpTrace>> tracesCaptor = traceCaptor();
        verify(mrpRepository).insertTraces(tracesCaptor.capture());
        assertEquals(6, tracesCaptor.getValue().size());
    }

    @Test
    @DisplayName("快照产品编码与需求物料不一致时快速失败")
    void shouldRejectProductCodeMismatch() {
        BomSnapshot snapshot = snapshot();
        snapshot.setProductCode("P2");
        when(bomCatalog.fetch("BOM-P1", "V1")).thenReturn(snapshot);
        stubItems();
        MrpRun run = run(10);

        assertThrows(BizException.class, () -> executor.execute(run));
        verify(mrpRepository, never()).insertPlannedOrders(any());
    }

    @Test
    @DisplayName("BOM 子件未维护时快速失败")
    void shouldRejectMissingItem() {
        stubSnapshot("BOM-P1", "V1");
        when(itemCatalog.findByCode("SA")).thenReturn(Optional.empty());
        MrpRun run = run(10);

        assertThrows(BizException.class, () -> executor.execute(run));
        verify(mrpRepository, never()).insertPlannedOrders(any());
    }

    @Test
    @DisplayName("PDM 拉取失败向上抛出")
    void shouldPropagateBomFetchFailure() {
        when(bomCatalog.fetch("BOM-P1", "V1"))
                .thenThrow(ErpErrorCode.exception(ErpErrorCode.MRP_BOM_FETCH_FAILED));

        assertThrows(BizException.class, () -> executor.execute(run(10)));
    }

    /**
     * 构造带计划订单 ID 回填的仓储桩。
     */
    private void assignOrderIds() {
        AtomicLong sequence = new AtomicLong(100);
        doAnswer(invocation -> {
            List<MrpPlannedOrder> orders = invocation.getArgument(0);
            orders.forEach(order -> order.setId(sequence.getAndIncrement()));
            return null;
        }).when(mrpRepository).insertPlannedOrders(any());
    }

    /**
     * 构造快照桩。
     *
     * @param bomNo BOM 编号
     * @param versionNo 版本
     */
    private void stubSnapshot(String bomNo, String versionNo) {
        when(bomCatalog.fetch(bomNo, versionNo)).thenReturn(snapshot());
    }

    /**
     * 构造物料桩。
     */
    private void stubItems() {
        when(itemCatalog.findByCode("SA")).thenReturn(Optional.of(item(11L, "SA")));
        when(itemCatalog.findByCode("C1")).thenReturn(Optional.of(item(12L, "C1")));
        when(itemCatalog.findByCode("C2")).thenReturn(Optional.of(item(13L, "C2")));
    }

    /**
     * 构造运行记录。
     *
     * @param quantity 需求数量
     * @return 运行记录
     */
    private MrpRun run(int quantity) {
        MrpRun run = new MrpRun();
        run.setId(1L);
        run.setRunNo("MRP-20261107-0001");
        run.setStatus(MfgStatus.RUNNING);
        MrpRunLine line = new MrpRunLine();
        line.setLineNo(1);
        line.setItemCode("P1");
        line.setBomNo("BOM-P1");
        line.setBomVersion("V1");
        line.setQuantity(new BigDecimal(quantity));
        line.setDueDate(LocalDate.of(2026, 11, 10));
        run.setLines(new ArrayList<>(List.of(line)));
        return run;
    }

    /**
     * 构造两子件、两层级的快照。
     *
     * @return 快照
     */
    private BomSnapshot snapshot() {
        BomSnapshot snapshot = new BomSnapshot();
        snapshot.setBomNo("BOM-P1");
        snapshot.setVersionNo("V1");
        snapshot.setProductCode("P1");
        snapshot.setLines(List.of(
                line("1", 1, "P1", "SA", "2"),
                line("1.1", 2, "SA", "C1", "3"),
                line("2", 1, "P1", "C2", "5")));
        return snapshot;
    }

    /**
     * 构造 BOM 行。
     *
     * @param path 路径
     * @param depth 层级
     * @param parent 父件
     * @param child 子件
     * @param qty 用量
     * @return BOM 行
     */
    private BomLine line(String path, int depth, String parent, String child, String qty) {
        BomLine line = new BomLine();
        line.setPath(path);
        line.setDepth(depth);
        line.setParentPartNo(parent);
        line.setChildPartNo(child);
        line.setChildPartName(child + "名称");
        line.setQty(new BigDecimal(qty));
        line.setUom("PCS");
        return line;
    }

    /**
     * 构造物料快照。
     *
     * @param id 主键
     * @param code 编码
     * @return 物料快照
     */
    private ItemSnapshot item(Long id, String code) {
        ItemSnapshot item = new ItemSnapshot();
        item.setId(id);
        item.setCode(code);
        item.setName(code + "名称");
        item.setStatus(MasterDataStatus.ENABLED);
        return item;
    }

    /**
     * 计划订单捕获器。
     *
     * @return 捕获器
     */
    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<MrpPlannedOrder>> orderCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    /**
     * 追溯行捕获器。
     *
     * @return 捕获器
     */
    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<MrpTrace>> traceCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}

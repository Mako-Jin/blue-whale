package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.mfg.application.command.ProductionCompleteCommand;
import com.bluewhale.erp.mfg.application.command.ProductionIssueCommand;
import com.bluewhale.erp.mfg.application.command.ProductionMaterialAdjustCommand;
import com.bluewhale.erp.mfg.application.command.ProductionOrderCreateCommand;
import com.bluewhale.erp.mfg.application.event.ProductionOrderReleasedEvent;
import com.bluewhale.erp.mfg.application.view.ProductionOrderView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.model.ProductionMaterial;
import com.bluewhale.erp.mfg.domain.model.ProductionOperation;
import com.bluewhale.erp.mfg.domain.model.ProductionOrder;
import com.bluewhale.erp.mfg.domain.model.RoutingSnapshot;
import com.bluewhale.erp.mfg.domain.model.RoutingStep;
import com.bluewhale.erp.mfg.domain.port.RoutingCatalog;
import com.bluewhale.erp.mfg.domain.repository.ProductionOrderRepository;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

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
 * {@link ProductionOrderAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class ProductionOrderAppServiceImplTest {

    private final ProductionOrderRepository productionOrderRepository = mock(ProductionOrderRepository.class);

    private final StockRepository stockRepository = mock(StockRepository.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);

    private final AutoVoucherAppService autoVoucherAppService = mock(AutoVoucherAppService.class);

    private final RoutingCatalog routingCatalog = mock(RoutingCatalog.class);

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final ProductionOrderAppServiceImpl service = new ProductionOrderAppServiceImpl(productionOrderRepository,
            stockRepository, itemCatalog, warehouseRepository, autoVoucherAppService, routingCatalog, eventPublisher);

    @Test
    @DisplayName("新增生产订单：快照产出物料、用料与仓库")
    void shouldCreateOrder() {
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item(3L, "ERP-001", "成品A")));
        when(itemCatalog.findByCode("ERP-002")).thenReturn(Optional.of(item(4L, "ERP-002", "材料B")));
        when(warehouseRepository.findById(7301L)).thenReturn(Optional.of(warehouse(7301L, "主仓")));
        when(warehouseRepository.findById(7302L)).thenReturn(Optional.of(warehouse(7302L, "成品仓")));
        when(productionOrderRepository.nextOrderNo(LocalDate.of(2026, 11, 4))).thenReturn("MO-20261104-0001");

        ProductionOrderView view = service.create(command());

        assertEquals("MO-20261104-0001", view.getOrderNo());
        assertEquals(MfgStatus.DRAFT, view.getStatus());
        assertEquals("成品仓", view.getTargetWarehouseName());
        assertEquals("材料B", view.getMaterials().get(0).getItemName());
        assertEquals(0, view.getMaterials().get(0).getIssuedQuantity().compareTo(BigDecimal.ZERO));
        verify(productionOrderRepository).insert(any(ProductionOrder.class));
    }

    @Test
    @DisplayName("下达生产订单")
    void shouldRelease() {
        ProductionOrder order = draftOrder();
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        ProductionOrderView view = service.release(1L);

        assertEquals(MfgStatus.RELEASED, view.getStatus());
        verify(productionOrderRepository).update(any(ProductionOrder.class));
        verify(eventPublisher).publishEvent(any(ProductionOrderReleasedEvent.class));
    }

    @Test
    @DisplayName("领料：从领料仓出库、累计材料成本")
    void shouldIssueMaterial() {
        ProductionOrder order = draftOrder();
        order.setStatus(MfgStatus.RELEASED);
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(stockRepository.findBalance(4L, 7301L)).thenReturn(Optional.of(balance()));

        ProductionOrderView view = service.issue(1L, issueCommand());

        assertEquals(MfgStatus.IN_PROGRESS, view.getStatus());
        assertEquals(0, view.getTotalMaterialCost().compareTo(new BigDecimal("25")));
        assertEquals(0, view.getMaterials().get(0).getIssuedQuantity().compareTo(new BigDecimal("5")));
        verify(stockRepository).saveBalance(any(StockBalance.class));
        verify(stockRepository).insertMovement(any(StockMovement.class));
        verify(productionOrderRepository).update(any(ProductionOrder.class));
    }

    @Test
    @DisplayName("领料仓无库存拒绝领料")
    void shouldRejectIssueWithoutStock() {
        ProductionOrder order = draftOrder();
        order.setStatus(MfgStatus.RELEASED);
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(stockRepository.findBalance(4L, 7301L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.issue(1L, issueCommand()));

        assertEquals(ErpErrorCode.STOCK_NOT_FOUND, exception.getCode());
        verify(stockRepository, never()).insertMovement(any());
    }

    @Test
    @DisplayName("部分完工：入库并生成领料/完工凭证")
    void shouldCompletePartially() {
        ProductionOrder order = issuedOrder();
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(stockRepository.findBalance(3L, 7302L)).thenReturn(Optional.empty());

        ProductionOrderView view = service.complete(1L, completeCommand("5"), 99L);

        assertEquals(MfgStatus.IN_PROGRESS, view.getStatus());
        assertEquals(0, view.getCompletedQuantity().compareTo(new BigDecimal("5")));
        verify(stockRepository).saveBalance(any(StockBalance.class));
        verify(stockRepository).insertMovement(any(StockMovement.class));
        verify(productionOrderRepository).update(any(ProductionOrder.class));
        verify(autoVoucherAppService).postProductionIssue(any(AutoVoucherCommand.class));
        verify(autoVoucherAppService).postProductionReceipt(any(AutoVoucherCommand.class));
    }

    @Test
    @DisplayName("存在未领完用料时拒绝完工")
    void shouldRejectCompleteWithUnissuedMaterial() {
        ProductionOrder order = draftOrder();
        order.setStatus(MfgStatus.IN_PROGRESS);
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        BizException exception = assertThrows(BizException.class,
                () -> service.complete(1L, completeCommand("5"), 99L));

        assertEquals(ErpErrorCode.MATERIAL_NOT_ISSUED, exception.getCode());
        verify(stockRepository, never()).insertMovement(any());
    }

    @Test
    @DisplayName("已完工订单结案")
    void shouldClose() {
        ProductionOrder order = issuedOrder();
        order.complete(new BigDecimal("10"), LocalDate.of(2026, 11, 4));
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        ProductionOrderView view = service.close(1L);

        assertEquals(MfgStatus.CLOSED, view.getStatus());
        verify(productionOrderRepository).update(any(ProductionOrder.class));
    }

    @Test
    @DisplayName("草稿订单可以取消")
    void shouldCancelDraft() {
        ProductionOrder order = draftOrder();
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        ProductionOrderView view = service.cancel(1L);

        assertEquals(MfgStatus.CANCELLED, view.getStatus());
    }

    @Test
    @DisplayName("分页查询返回总条数")
    void shouldPage() {
        when(productionOrderRepository.page(any())).thenReturn(List.of(draftOrder()));
        when(productionOrderRepository.count(any())).thenReturn(1L);

        var page = service.page(new MfgQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    @Test
    @DisplayName("建单：冻结 PDM 已发布工艺路线工序")
    void shouldFreezeRoutingOnCreate() {
        stubCreateDependencies();
        when(routingCatalog.latestReleased("ERP-001")).thenReturn(Optional.of(snapshot()));

        ProductionOrderView view = service.create(command());

        assertEquals(MfgStatus.ROUTING_SNAPSHOT, view.getRoutingStatus());
        assertEquals("RT-ERP-001", view.getRoutingNo());
        assertEquals("V1.0", view.getRoutingVersion());
        assertEquals(2, view.getOperations().size());
        assertEquals(10, view.getOperations().get(0).getStepNo());
    }

    @Test
    @DisplayName("建单：无已发布路线时标记 NONE")
    void shouldMarkRoutingNone() {
        stubCreateDependencies();
        when(routingCatalog.latestReleased("ERP-001")).thenReturn(Optional.empty());

        ProductionOrderView view = service.create(command());

        assertEquals(MfgStatus.ROUTING_NONE, view.getRoutingStatus());
        assertEquals(0, view.getOperations().size());
    }

    @Test
    @DisplayName("建单：PDM 不可达时标记 FAILED 且不阻断建单")
    void shouldMarkRoutingFailed() {
        stubCreateDependencies();
        when(routingCatalog.latestReleased("ERP-001"))
                .thenThrow(ErpErrorCode.exception(ErpErrorCode.ROUTING_FETCH_FAILED));

        ProductionOrderView view = service.create(command());

        assertEquals(MfgStatus.ROUTING_FAILED, view.getRoutingStatus());
        assertEquals(0, view.getOperations().size());
    }

    @Test
    @DisplayName("同步工艺：覆盖草稿订单工序快照")
    void shouldSyncRouting() {
        ProductionOrder order = draftOrder();
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(routingCatalog.latestReleased("ERP-001")).thenReturn(Optional.of(snapshot()));

        ProductionOrderView view = service.syncRouting(1L);

        assertEquals(MfgStatus.ROUTING_SNAPSHOT, view.getRoutingStatus());
        assertEquals(2, view.getOperations().size());
        verify(productionOrderRepository).updateRouting(order);
    }

    @Test
    @DisplayName("同步工艺：已有报工拒绝覆盖")
    void shouldRejectSyncRoutingAfterReport() {
        ProductionOrder order = draftOrder();
        order.setStatus(MfgStatus.RELEASED);
        ProductionOperation operation = new ProductionOperation();
        operation.setStepNo(10);
        operation.setPlannedQuantity(new BigDecimal("10"));
        operation.setQualifiedQuantity(new BigDecimal("1"));
        operation.setScrappedQuantity(BigDecimal.ZERO);
        order.setOperations(List.of(operation));
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(BizException.class, () -> service.syncRouting(1L));
    }

    @Test
    @DisplayName("倒冲领料：完工按比例自动领料并计入成本")
    void shouldAutoIssueBackflushOnComplete() {
        ProductionOrder order = draftOrder();
        order.setStatus(MfgStatus.IN_PROGRESS);
        order.getMaterials().get(0).setBackflush(true);
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(stockRepository.findBalance(4L, 7301L)).thenReturn(Optional.of(balance()));
        when(stockRepository.findBalance(3L, 7302L)).thenReturn(Optional.empty());

        ProductionOrderView view = service.complete(1L, completeCommand("5"), 99L);

        assertEquals(0, view.getTotalMaterialCost().compareTo(new BigDecimal("25")));
        assertEquals(0, view.getMaterials().get(0).getIssuedQuantity().compareTo(new BigDecimal("5")));
        verify(stockRepository, times(2)).insertMovement(any(StockMovement.class));
    }

    @Test
    @DisplayName("退料：按已领成本回冲库存并冲减材料成本")
    void shouldReturnMaterial() {
        ProductionOrder order = issuedOrder();
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productionOrderRepository.nextAdjustmentNo(any(LocalDate.class))).thenReturn("MA-20261104-0001");
        when(stockRepository.findBalance(4L, 7301L)).thenReturn(Optional.of(balance()));

        ProductionOrderView view = service.returnMaterial(1L, adjustCommand("3"), 99L);

        assertEquals(0, view.getTotalMaterialCost().compareTo(new BigDecimal("70")));
        assertEquals(0, view.getMaterials().get(0).getIssuedQuantity().compareTo(new BigDecimal("7")));
        verify(stockRepository).saveBalance(any(StockBalance.class));
        verify(stockRepository).insertMovement(any(StockMovement.class));
        verify(productionOrderRepository).insertAdjustment(any());
    }

    @Test
    @DisplayName("报废：扣减已领成本且不回库存")
    void shouldScrapMaterial() {
        ProductionOrder order = issuedOrder();
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productionOrderRepository.nextAdjustmentNo(any(LocalDate.class))).thenReturn("MA-20261104-0002");

        ProductionOrderView view = service.scrapMaterial(1L, adjustCommand("3"), 99L);

        assertEquals(0, view.getTotalMaterialCost().compareTo(new BigDecimal("70")));
        verify(stockRepository, never()).saveBalance(any());
        verify(stockRepository, never()).insertMovement(any());
        verify(productionOrderRepository).insertAdjustment(any());
    }

    /**
     * 构造用料调整命令。
     *
     * @param quantity 数量
     * @return 命令
     */
    private ProductionMaterialAdjustCommand adjustCommand(String quantity) {
        ProductionMaterialAdjustCommand command = new ProductionMaterialAdjustCommand();
        ProductionMaterialAdjustCommand.Line line = new ProductionMaterialAdjustCommand.Line();
        line.setLineNo(1);
        line.setQuantity(new BigDecimal(quantity));
        line.setReason("测试");
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 建单依赖桩。
     */
    private void stubCreateDependencies() {
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item(3L, "ERP-001", "成品A")));
        when(itemCatalog.findByCode("ERP-002")).thenReturn(Optional.of(item(4L, "ERP-002", "材料B")));
        when(warehouseRepository.findById(7301L)).thenReturn(Optional.of(warehouse(7301L, "主仓")));
        when(warehouseRepository.findById(7302L)).thenReturn(Optional.of(warehouse(7302L, "成品仓")));
        when(productionOrderRepository.nextOrderNo(LocalDate.of(2026, 11, 4))).thenReturn("MO-20261104-0001");
    }

    /**
     * 构造工艺路线快照。
     *
     * @return 快照
     */
    private RoutingSnapshot snapshot() {
        RoutingSnapshot snapshot = new RoutingSnapshot();
        snapshot.setRouteNo("RT-ERP-001");
        snapshot.setVersionNo("V1.0");
        snapshot.setPartCode("ERP-001");
        snapshot.setSteps(List.of(step(10, "下料", "WC1"), step(20, "装配", "WC2")));
        return snapshot;
    }

    /**
     * 构造路线工序。
     *
     * @param stepNo     序号
     * @param name       名称
     * @param workCenter 工作中心
     * @return 工序
     */
    private RoutingStep step(Integer stepNo, String name, String workCenter) {
        RoutingStep step = new RoutingStep();
        step.setStepNo(stepNo);
        step.setName(name);
        step.setWorkCenterCode(workCenter);
        step.setStandardHours(new BigDecimal("2"));
        step.setSetupHours(BigDecimal.ONE);
        return step;
    }

    /**
     * 构造创建命令。
     *
     * @return 命令
     */
    private ProductionOrderCreateCommand command() {
        ProductionOrderCreateCommand command = new ProductionOrderCreateCommand();
        command.setItemCode("ERP-001");
        command.setPlannedQuantity(new BigDecimal("10"));
        command.setIssueWarehouseId(7301L);
        command.setTargetWarehouseId(7302L);
        command.setPlanStartDate(LocalDate.of(2026, 11, 4));
        ProductionOrderCreateCommand.Material material = new ProductionOrderCreateCommand.Material();
        material.setItemCode("ERP-002");
        material.setRequiredQuantity(new BigDecimal("10"));
        command.setMaterials(List.of(material));
        return command;
    }

    /**
     * 构造领料命令。
     *
     * @return 命令
     */
    private ProductionIssueCommand issueCommand() {
        ProductionIssueCommand command = new ProductionIssueCommand();
        ProductionIssueCommand.Line line = new ProductionIssueCommand.Line();
        line.setLineNo(1);
        line.setQuantity(new BigDecimal("5"));
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造完工命令。
     *
     * @param quantity 完工数量
     * @return 命令
     */
    private ProductionCompleteCommand completeCommand(String quantity) {
        ProductionCompleteCommand command = new ProductionCompleteCommand();
        command.setCompleteDate(LocalDate.of(2026, 11, 4));
        command.setQuantity(new BigDecimal(quantity));
        return command;
    }

    /**
     * 构造草稿生产订单。
     *
     * @return 生产订单
     */
    private ProductionOrder draftOrder() {
        ProductionOrder order = new ProductionOrder();
        order.setId(1L);
        order.setOrderNo("MO-20261104-0001");
        order.setItemId(3L);
        order.setItemCode("ERP-001");
        order.setItemName("成品A");
        order.setPlannedQuantity(new BigDecimal("10"));
        order.setCompletedQuantity(BigDecimal.ZERO);
        order.setCompletedCost(BigDecimal.ZERO);
        order.setTotalMaterialCost(BigDecimal.ZERO);
        order.setIssueWarehouseId(7301L);
        order.setIssueWarehouseName("主仓");
        order.setTargetWarehouseId(7302L);
        order.setTargetWarehouseName("成品仓");
        order.setPlanStartDate(LocalDate.of(2026, 11, 4));
        order.setStatus(MfgStatus.DRAFT);
        ProductionMaterial material = new ProductionMaterial();
        material.setLineNo(1);
        material.setItemId(4L);
        material.setItemCode("ERP-002");
        material.setItemName("材料B");
        material.setRequiredQuantity(new BigDecimal("10"));
        material.setIssuedQuantity(BigDecimal.ZERO);
        order.setMaterials(List.of(material));
        return order;
    }

    /**
     * 构造已领料、生产中订单。
     *
     * @return 生产订单
     */
    private ProductionOrder issuedOrder() {
        ProductionOrder order = draftOrder();
        order.setStatus(MfgStatus.RELEASED);
        order.issue(order.getMaterials().get(0), new BigDecimal("10"));
        order.getMaterials().get(0).accumulateIssuedCost(new BigDecimal("100"));
        order.accumulateMaterialCost(new BigDecimal("100"));
        return order;
    }

    /**
     * 构造库存余额。
     *
     * @return 余额
     */
    private StockBalance balance() {
        StockBalance balance = new StockBalance();
        balance.setId(10L);
        balance.setItemId(4L);
        balance.setItemCode("ERP-002");
        balance.setItemName("材料B");
        balance.setWarehouseId(7301L);
        balance.setQuantity(new BigDecimal("10"));
        balance.setAmount(new BigDecimal("50"));
        return balance;
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

    /**
     * 构造物料快照。
     *
     * @param id 主键
     * @param code 编码
     * @param name 名称
     * @return 快照
     */
    private ItemSnapshot item(Long id, String code, String name) {
        ItemSnapshot snapshot = new ItemSnapshot();
        snapshot.setId(id);
        snapshot.setCode(code);
        snapshot.setName(name);
        snapshot.setStatus(MasterDataStatus.ENABLED);
        return snapshot;
    }
}

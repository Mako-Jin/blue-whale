package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.mfg.application.command.ProductionCompleteCommand;
import com.bluewhale.erp.mfg.application.command.ProductionIssueCommand;
import com.bluewhale.erp.mfg.application.command.ProductionMaterialAdjustCommand;
import com.bluewhale.erp.mfg.application.command.ProductionOperationReportCommand;
import com.bluewhale.erp.mfg.application.command.ProductionOrderCreateCommand;
import com.bluewhale.erp.mfg.application.service.ProductionOrderAppService;
import com.bluewhale.erp.mfg.application.view.ProductionOrderView;
import com.bluewhale.erp.mfg.domain.model.MaterialAdjustment;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.model.ProductionMaterial;
import com.bluewhale.erp.mfg.domain.model.ProductionOperation;
import com.bluewhale.erp.mfg.domain.model.ProductionOrder;
import com.bluewhale.erp.mfg.domain.model.RoutingSnapshot;
import com.bluewhale.erp.mfg.domain.model.RoutingStep;
import com.bluewhale.erp.mfg.domain.port.RoutingCatalog;
import com.bluewhale.erp.mfg.domain.repository.ProductionOrderRepository;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 生产订单应用服务实现。
 *
 * <p>领料从领料仓按移动加权平均出库并累计材料成本；完工入产出仓，最后一批完工时结转剩余成本。
 * 完工时按整单生成「生产领料」凭证（借生产成本、贷存货），并按完工批次生成「完工入库」凭证
 * （借存货、贷生产成本），来源单据号幂等。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ProductionOrderAppServiceImpl implements ProductionOrderAppService {

    /** 单位成本小数位 */
    private static final int COST_SCALE = 4;

    private final ProductionOrderRepository productionOrderRepository;

    private final StockRepository stockRepository;

    private final ItemCatalog itemCatalog;

    private final WarehouseRepository warehouseRepository;

    private final AutoVoucherAppService autoVoucherAppService;

    private final RoutingCatalog routingCatalog;

    @Override
    public PageResult<ProductionOrderView> page(MfgQuery query) {
        List<ProductionOrderView> records = productionOrderRepository.page(query).stream()
                .map(ProductionOrderView::of).toList();
        return PageResult.of(records, productionOrderRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public ProductionOrderView detail(Long id) {
        return ProductionOrderView.of(requireOrder(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView create(ProductionOrderCreateCommand command) {
        ItemSnapshot product = requireItem(command.getItemCode());
        Warehouse issueWarehouse = requireWarehouse(command.getIssueWarehouseId());
        Warehouse targetWarehouse = requireWarehouse(command.getTargetWarehouseId());
        List<ProductionMaterial> materials = new ArrayList<>();
        int lineNo = 1;
        for (ProductionOrderCreateCommand.Material source : command.getMaterials()) {
            ItemSnapshot item = requireItem(source.getItemCode());
            ProductionMaterial material = new ProductionMaterial();
            material.setLineNo(lineNo++);
            material.setItemId(item.getId());
            material.setItemCode(item.getCode());
            material.setItemName(item.getName());
            material.setUom(source.getUom());
            material.setRequiredQuantity(source.getRequiredQuantity());
            material.setIssuedQuantity(BigDecimal.ZERO);
            material.setIssuedCost(BigDecimal.ZERO);
            material.setBackflush(Boolean.TRUE.equals(source.getBackflush()));
            materials.add(material);
        }
        ProductionOrder order = new ProductionOrder();
        order.setOrderNo(productionOrderRepository.nextOrderNo(command.getPlanStartDate()));
        order.setItemId(product.getId());
        order.setItemCode(product.getCode());
        order.setItemName(product.getName());
        order.setPlannedQuantity(command.getPlannedQuantity());
        order.setCompletedQuantity(BigDecimal.ZERO);
        order.setCompletedCost(BigDecimal.ZERO);
        order.setTotalMaterialCost(BigDecimal.ZERO);
        order.setIssueWarehouseId(issueWarehouse.getId());
        order.setIssueWarehouseName(issueWarehouse.getName());
        order.setTargetWarehouseId(targetWarehouse.getId());
        order.setTargetWarehouseName(targetWarehouse.getName());
        order.setPlanStartDate(command.getPlanStartDate());
        order.setPlanEndDate(command.getPlanEndDate());
        order.setStatus(MfgStatus.DRAFT);
        order.setRemark(command.getRemark());
        order.setMaterials(materials);
        applyRouting(order);
        order.validateForCreate();
        productionOrderRepository.insert(order);
        return ProductionOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView syncRouting(Long id) {
        ProductionOrder order = requireOrder(id);
        boolean editable = MfgStatus.DRAFT.equals(order.getStatus()) || MfgStatus.RELEASED.equals(order.getStatus());
        if (!editable) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID, "仅草稿或已下达订单可同步工艺路线");
        }
        if (order.getOperations() != null && order.getOperations().stream()
                .anyMatch(operation -> operation.reportedQuantity().signum() > 0)) {
            throw ErpErrorCode.exception(ErpErrorCode.OPERATION_INVALID, "订单已有工序报工，不能覆盖工艺路线");
        }
        RoutingSnapshot snapshot = routingCatalog.latestReleased(order.getItemCode())
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ROUTING_FETCH_FAILED,
                        "零件 " + order.getItemCode() + " 没有已发布工艺路线"));
        freezeRouting(order, snapshot);
        productionOrderRepository.updateRouting(order);
        return ProductionOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView release(Long id) {
        ProductionOrder order = requireOrder(id);
        order.release();
        productionOrderRepository.update(order);
        return ProductionOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView issue(Long id, ProductionIssueCommand command) {
        ProductionOrder order = requireOrder(id);
        String orderNo = order.getOrderNo();
        LocalDateTime occurredAt = LocalDateTime.now();
        for (ProductionIssueCommand.Line source : command.getLines()) {
            ProductionMaterial material = requireMaterial(order, source.getLineNo());
            order.issue(material, source.getQuantity());
            StockBalance balance = stockRepository.findBalance(material.getItemId(), order.getIssueWarehouseId())
                    .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.STOCK_NOT_FOUND,
                            "物料 " + material.getItemCode() + " 在领料仓无库存"));
            BigDecimal quantity = source.getQuantity();
            BigDecimal cost = balance.outbound(quantity);
            stockRepository.saveBalance(balance);
            BigDecimal unitCost = cost.divide(quantity, COST_SCALE, RoundingMode.HALF_UP);
            stockRepository.insertMovement(movement(orderNo, material, order.getIssueWarehouseId(), ScmStatus.OUT,
                    quantity, unitCost, cost, occurredAt, MfgStatus.SOURCE_PRODUCTION_ISSUE,
                    "生产领料：" + orderNo));
            material.accumulateIssuedCost(cost);
            order.accumulateMaterialCost(cost);
        }
        productionOrderRepository.update(order);
        return ProductionOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView complete(Long id, ProductionCompleteCommand command, Long operatorId) {
        ProductionOrder order = requireOrder(id);
        BigDecimal quantity = command.getQuantity();
        autoIssueBackflush(order, quantity);
        BigDecimal amount = order.complete(quantity, command.getCompleteDate());
        String orderNo = order.getOrderNo();
        BigDecimal unitCost = amount.divide(quantity, COST_SCALE, RoundingMode.HALF_UP);
        StockBalance balance = stockRepository.findBalance(order.getItemId(), order.getTargetWarehouseId())
                .orElseGet(() -> newBalance(order));
        balance.inbound(quantity, unitCost);
        stockRepository.saveBalance(balance);
        stockRepository.insertMovement(movement(orderNo, order, order.getTargetWarehouseId(), ScmStatus.IN,
                quantity, unitCost, amount, LocalDateTime.now(), MfgStatus.SOURCE_PRODUCTION_RECEIPT,
                "生产完工入库：" + orderNo));
        productionOrderRepository.update(order);
        postVouchers(order, amount, operatorId);
        return ProductionOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView reportOperation(Long id, Integer stepNo, ProductionOperationReportCommand command) {
        ProductionOrder order = requireOrder(id);
        order.reportOperation(stepNo, command.getQualifiedQuantity(), command.getScrappedQuantity(),
                LocalDateTime.now());
        productionOrderRepository.update(order);
        return ProductionOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView returnMaterial(Long id, ProductionMaterialAdjustCommand command, Long operatorId) {
        ProductionOrder order = requireOrder(id);
        String docNo = productionOrderRepository.nextAdjustmentNo(LocalDate.now());
        LocalDateTime occurredAt = LocalDateTime.now();
        for (ProductionMaterialAdjustCommand.Line line : command.getLines()) {
            ProductionMaterial material = requireMaterial(order, line.getLineNo());
            BigDecimal cost = order.returnMaterial(material, line.getQuantity());
            StockBalance balance = stockRepository.findBalance(material.getItemId(), order.getIssueWarehouseId())
                    .orElseGet(() -> newMaterialBalance(order, material));
            balance.inboundAt(line.getQuantity(), cost);
            stockRepository.saveBalance(balance);
            BigDecimal unitCost = cost.divide(line.getQuantity(), COST_SCALE, RoundingMode.HALF_UP);
            stockRepository.insertMovement(movement(order.getOrderNo(), material, order.getIssueWarehouseId(),
                    ScmStatus.IN, line.getQuantity(), unitCost, cost, occurredAt,
                    MfgStatus.SOURCE_PRODUCTION_RETURN, "生产退料：" + order.getOrderNo()));
            productionOrderRepository.insertAdjustment(adjustment(docNo, MfgStatus.ADJ_RETURN, order, material,
                    line, cost, operatorId, occurredAt));
        }
        productionOrderRepository.update(order);
        return ProductionOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView scrapMaterial(Long id, ProductionMaterialAdjustCommand command, Long operatorId) {
        ProductionOrder order = requireOrder(id);
        String docNo = productionOrderRepository.nextAdjustmentNo(LocalDate.now());
        LocalDateTime occurredAt = LocalDateTime.now();
        for (ProductionMaterialAdjustCommand.Line line : command.getLines()) {
            ProductionMaterial material = requireMaterial(order, line.getLineNo());
            BigDecimal cost = order.scrapMaterial(material, line.getQuantity());
            productionOrderRepository.insertAdjustment(adjustment(docNo, MfgStatus.ADJ_SCRAP, order, material,
                    line, cost, operatorId, occurredAt));
        }
        productionOrderRepository.update(order);
        return ProductionOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView close(Long id) {
        ProductionOrder order = requireOrder(id);
        order.close();
        productionOrderRepository.update(order);
        return ProductionOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView cancel(Long id) {
        ProductionOrder order = requireOrder(id);
        order.cancel();
        productionOrderRepository.update(order);
        return ProductionOrderView.of(order);
    }

    /**
     * 完工前按完工比例自动领用倒冲料。
     *
     * @param order         生产订单
     * @param batchQuantity 本次完工数量
     */
    private void autoIssueBackflush(ProductionOrder order, BigDecimal batchQuantity) {
        if (order.getMaterials() == null) {
            return;
        }
        BigDecimal completed = order.getCompletedQuantity() == null
                ? BigDecimal.ZERO : order.getCompletedQuantity();
        LocalDateTime occurredAt = LocalDateTime.now();
        for (ProductionMaterial material : order.getMaterials()) {
            if (!Boolean.TRUE.equals(material.getBackflush())) {
                continue;
            }
            BigDecimal target = material.backflushTarget(completed, batchQuantity, order.getPlannedQuantity());
            BigDecimal issued = material.getIssuedQuantity() == null
                    ? BigDecimal.ZERO : material.getIssuedQuantity();
            BigDecimal delta = target.subtract(issued);
            if (delta.signum() <= 0) {
                continue;
            }
            BigDecimal cost = outboundForBackflush(order, material, delta, occurredAt);
            material.issue(delta);
            material.accumulateIssuedCost(cost);
            order.accumulateMaterialCost(cost);
        }
    }

    /**
     * 倒冲领料出库并写库存流水。
     *
     * @param order      生产订单
     * @param material   用料
     * @param quantity   领料数量
     * @param occurredAt 发生时间
     * @return 领料成本
     */
    private BigDecimal outboundForBackflush(ProductionOrder order, ProductionMaterial material, BigDecimal quantity,
                                            LocalDateTime occurredAt) {
        StockBalance balance = stockRepository.findBalance(material.getItemId(), order.getIssueWarehouseId())
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.STOCK_NOT_FOUND,
                        "物料 " + material.getItemCode() + " 在领料仓无库存，倒冲领料失败"));
        BigDecimal cost = balance.outbound(quantity);
        stockRepository.saveBalance(balance);
        BigDecimal unitCost = cost.divide(quantity, COST_SCALE, RoundingMode.HALF_UP);
        stockRepository.insertMovement(movement(order.getOrderNo(), material, order.getIssueWarehouseId(), ScmStatus.OUT,
                quantity, unitCost, cost, occurredAt, MfgStatus.SOURCE_PRODUCTION_ISSUE,
                "倒冲领料：" + order.getOrderNo()));
        return cost;
    }

    /**
     * 构造退料入库新余额。
     *
     * @param order    生产订单
     * @param material 用料
     * @return 余额
     */
    private StockBalance newMaterialBalance(ProductionOrder order, ProductionMaterial material) {
        StockBalance balance = new StockBalance();
        balance.setItemId(material.getItemId());
        balance.setItemCode(material.getItemCode());
        balance.setItemName(material.getItemName());
        balance.setWarehouseId(order.getIssueWarehouseId());
        balance.setQuantity(BigDecimal.ZERO);
        balance.setAmount(BigDecimal.ZERO);
        return balance;
    }

    /**
     * 构造用料调整记录。
     *
     * @param docNo      调整单号
     * @param adjType    调整类型
     * @param order      生产订单
     * @param material   用料
     * @param line       调整分录
     * @param amount     调整金额
     * @param operatorId 操作人 ID
     * @param occurredAt 发生时间
     * @return 调整记录
     */
    private MaterialAdjustment adjustment(String docNo, String adjType, ProductionOrder order,
                                          ProductionMaterial material, ProductionMaterialAdjustCommand.Line line,
                                          BigDecimal amount, Long operatorId, LocalDateTime occurredAt) {
        MaterialAdjustment adjustment = new MaterialAdjustment();
        adjustment.setDocNo(docNo);
        adjustment.setAdjType(adjType);
        adjustment.setOrderId(order.getId());
        adjustment.setLineNo(material.getLineNo());
        adjustment.setItemId(material.getItemId());
        adjustment.setItemCode(material.getItemCode());
        adjustment.setItemName(material.getItemName());
        adjustment.setQuantity(line.getQuantity());
        adjustment.setAmount(amount);
        adjustment.setReason(line.getReason());
        adjustment.setOperatorId(operatorId);
        adjustment.setOccurredAt(occurredAt);
        return adjustment;
    }

    /**
     * 建单时拉取工艺路线：成功冻结工序，404 记 NONE，不可达记 FAILED。
     *
     * @param order 生产订单
     */
    private void applyRouting(ProductionOrder order) {
        try {
            routingCatalog.latestReleased(order.getItemCode())
                    .ifPresentOrElse(
                            snapshot -> freezeRouting(order, snapshot),
                            () -> {
                                order.setRoutingStatus(MfgStatus.ROUTING_NONE);
                                order.setOperations(new ArrayList<>());
                            });
        } catch (BizException ex) {
            if (ex.getCode() != ErpErrorCode.ROUTING_FETCH_FAILED) {
                throw ex;
            }
            order.setRoutingStatus(MfgStatus.ROUTING_FAILED);
            order.setOperations(new ArrayList<>());
        }
    }

    /**
     * 冻结工艺路线到订单工序。
     *
     * @param order    生产订单
     * @param snapshot 路线快照
     */
    private void freezeRouting(ProductionOrder order, RoutingSnapshot snapshot) {
        order.setRoutingNo(snapshot.getRouteNo());
        order.setRoutingVersion(snapshot.getVersionNo());
        order.setRoutingStatus(MfgStatus.ROUTING_SNAPSHOT);
        List<ProductionOperation> operations = new ArrayList<>();
        snapshot.getSteps().stream()
                .sorted(Comparator.comparing(RoutingStep::getStepNo))
                .forEach(step -> {
                    ProductionOperation operation = new ProductionOperation();
                    operation.setStepNo(step.getStepNo());
                    operation.setName(step.getName());
                    operation.setWorkCenterCode(step.getWorkCenterCode());
                    operation.setStandardHours(step.getStandardHours() == null
                            ? BigDecimal.ZERO : step.getStandardHours());
                    operation.setSetupHours(step.getSetupHours() == null
                            ? BigDecimal.ZERO : step.getSetupHours());
                    operation.setPlannedQuantity(order.getPlannedQuantity());
                    operation.setQualifiedQuantity(BigDecimal.ZERO);
                    operation.setScrappedQuantity(BigDecimal.ZERO);
                    operation.setStatus(MfgStatus.PENDING);
                    operations.add(operation);
                });
        order.setOperations(operations);
    }

    /**
     * 生成领料与完工凭证。
     *
     * @param order 生产订单
     * @param receiptAmount 本次完工入库金额
     * @param operatorId 操作人 ID
     */
    private void postVouchers(ProductionOrder order, BigDecimal receiptAmount, Long operatorId) {
        if (order.getTotalMaterialCost().signum() > 0) {
            autoVoucherAppService.postProductionIssue(voucher(MfgStatus.SOURCE_PRODUCTION_ISSUE,
                    order.getOrderNo(), order.getCompleteDate(), order.getTotalMaterialCost(), operatorId,
                    "生产领料：" + order.getOrderNo()));
        }
        String suffix = order.getCompletedQuantity().stripTrailingZeros().toPlainString();
        autoVoucherAppService.postProductionReceipt(voucher(MfgStatus.SOURCE_PRODUCTION_RECEIPT,
                order.getOrderNo() + "-C" + suffix, order.getCompleteDate(), receiptAmount, operatorId,
                "生产完工入库：" + order.getOrderNo()));
    }

    /**
     * 构造自动记账命令。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源单据号
     * @param voucherDate 凭证日期
     * @param amount 金额
     * @param operatorId 操作人 ID
     * @param remark 备注
     * @return 记账命令
     */
    private AutoVoucherCommand voucher(String sourceType, String sourceId, LocalDate voucherDate,
                                       BigDecimal amount, Long operatorId, String remark) {
        AutoVoucherCommand command = new AutoVoucherCommand();
        command.setSourceType(sourceType);
        command.setSourceId(sourceId);
        command.setVoucherDate(voucherDate);
        command.setAmount(amount);
        command.setOperatorId(operatorId);
        command.setRemark(remark);
        return command;
    }

    /**
     * 查询生产订单，不存在按 404 处理。
     *
     * @param id 主键
     * @return 生产订单
     */
    private ProductionOrder requireOrder(Long id) {
        return productionOrderRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PRODUCTION_ORDER_NOT_FOUND));
    }

    /**
     * 按行号查询用料。
     *
     * @param order 生产订单
     * @param lineNo 行号
     * @return 用料
     */
    private ProductionMaterial requireMaterial(ProductionOrder order, Integer lineNo) {
        return order.getMaterials().stream()
                .filter(material -> material.getLineNo().equals(lineNo))
                .findFirst()
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.MFG_INVALID,
                        "生产订单不存在用料行 " + lineNo));
    }

    /**
     * 校验物料存在且启用。
     *
     * @param itemCode 物料编码
     * @return 物料快照
     */
    private ItemSnapshot requireItem(String itemCode) {
        ItemSnapshot item = itemCatalog.findByCode(itemCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + itemCode + " 不存在"));
        if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + item.getCode() + " 已停用");
        }
        return item;
    }

    /**
     * 校验仓库存在且启用。
     *
     * @param warehouseId 仓库主键
     * @return 仓库
     */
    private Warehouse requireWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.WAREHOUSE_NOT_FOUND));
        if (!MasterDataStatus.ENABLED.equals(warehouse.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_INVALID, "仓库 " + warehouse.getCode() + " 已停用");
        }
        return warehouse;
    }

    /**
     * 构造领料库存流水。
     *
     * @param orderNo 生产订单号
     * @param material 用料
     * @param warehouseId 仓库主键
     * @param direction 方向
     * @param quantity 数量
     * @param unitCost 单位成本
     * @param amount 金额
     * @param occurredAt 发生时间
     * @param sourceType 来源类型
     * @param remark 备注
     * @return 库存流水
     */
    private StockMovement movement(String orderNo, ProductionMaterial material, Long warehouseId, String direction,
                                   BigDecimal quantity, BigDecimal unitCost, BigDecimal amount,
                                   LocalDateTime occurredAt, String sourceType, String remark) {
        StockMovement movement = new StockMovement();
        movement.setDocNo(orderNo);
        movement.setSourceType(sourceType);
        movement.setSourceId(orderNo);
        movement.setItemId(material.getItemId());
        movement.setItemCode(material.getItemCode());
        movement.setItemName(material.getItemName());
        movement.setWarehouseId(warehouseId);
        movement.setDirection(direction);
        movement.setQuantity(quantity);
        movement.setUnitCost(unitCost);
        movement.setAmount(amount);
        movement.setOccurredAt(occurredAt);
        movement.setRemark(remark);
        return movement;
    }

    /**
     * 构造完工入库库存流水。
     *
     * @param orderNo 生产订单号
     * @param order 生产订单
     * @param warehouseId 仓库主键
     * @param direction 方向
     * @param quantity 数量
     * @param unitCost 单位成本
     * @param amount 金额
     * @param occurredAt 发生时间
     * @param sourceType 来源类型
     * @param remark 备注
     * @return 库存流水
     */
    private StockMovement movement(String orderNo, ProductionOrder order, Long warehouseId, String direction,
                                   BigDecimal quantity, BigDecimal unitCost, BigDecimal amount,
                                   LocalDateTime occurredAt, String sourceType, String remark) {
        StockMovement movement = new StockMovement();
        movement.setDocNo(orderNo);
        movement.setSourceType(sourceType);
        movement.setSourceId(orderNo);
        movement.setItemId(order.getItemId());
        movement.setItemCode(order.getItemCode());
        movement.setItemName(order.getItemName());
        movement.setWarehouseId(warehouseId);
        movement.setDirection(direction);
        movement.setQuantity(quantity);
        movement.setUnitCost(unitCost);
        movement.setAmount(amount);
        movement.setOccurredAt(occurredAt);
        movement.setRemark(remark);
        return movement;
    }

    /**
     * 构造完工入库新余额。
     *
     * @param order 生产订单
     * @return 余额
     */
    private StockBalance newBalance(ProductionOrder order) {
        StockBalance balance = new StockBalance();
        balance.setItemId(order.getItemId());
        balance.setItemCode(order.getItemCode());
        balance.setItemName(order.getItemName());
        balance.setWarehouseId(order.getTargetWarehouseId());
        balance.setQuantity(BigDecimal.ZERO);
        balance.setAmount(BigDecimal.ZERO);
        return balance;
    }
}

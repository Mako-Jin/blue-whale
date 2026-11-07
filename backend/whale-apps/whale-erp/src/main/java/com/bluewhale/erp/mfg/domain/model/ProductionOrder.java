package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 生产订单聚合。
 *
 * <p>不变量：产出物料与用料不能为空；计划数量为正；只有草稿可下达；
 * 领料数量不得超过需求数量；完工前全部用料必须领完；完工数量不得超过计划数量。
 * 完工入库单位成本按「累计领料成本 / 计划数量」计算，最后一批完工时结转剩余成本。</p>
 *
 * @author BlueWhale
 */
@Data
public class ProductionOrder {

    /** 单位成本小数位 */
    private static final int COST_SCALE = 4;

    /** 主键 */
    private Long id;

    /** 生产订单号 */
    private String orderNo;

    /** 产出物料主键 */
    private Long itemId;

    /** 产出物料编码快照 */
    private String itemCode;

    /** 产出物料名称快照 */
    private String itemName;

    /** 计划生产数量 */
    private BigDecimal plannedQuantity;

    /** 已完工数量 */
    private BigDecimal completedQuantity;

    /** 已入库成本 */
    private BigDecimal completedCost;

    /** 累计领料成本 */
    private BigDecimal totalMaterialCost;

    /** 领料仓库 */
    private Long issueWarehouseId;

    /** 领料仓库名称快照 */
    private String issueWarehouseName;

    /** 完工入库仓库 */
    private Long targetWarehouseId;

    /** 完工入库仓库名称快照 */
    private String targetWarehouseName;

    /** 计划开工日期 */
    private LocalDate planStartDate;

    /** 计划完工日期 */
    private LocalDate planEndDate;

    /** 实际完工日期 */
    private LocalDate completeDate;

    /** 状态：DRAFT / RELEASED / IN_PROGRESS / COMPLETED / CLOSED / CANCELLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 工艺路线编号快照 */
    private String routingNo;

    /** 工艺路线版本快照 */
    private String routingVersion;

    /** 工艺路线状态：NONE / SNAPSHOT / FAILED */
    private String routingStatus;

    /** MES 下发状态：NOT_SYNCED / PUSHED / FAILED */
    private String mesSyncStatus;

    /** MES 工单编码 */
    private String mesWorkOrderCode;

    /** 最近成功下发时间 */
    private LocalDateTime mesSyncedAt;

    /** 最近一次下发/拉取失败摘要 */
    private String mesLastError;

    /** 用料 */
    private List<ProductionMaterial> materials;

    /** 工序（PDM 路线快照） */
    private List<ProductionOperation> operations;

    /**
     * 校验建单数据。
     */
    public void validateForCreate() {
        if (itemCode == null || itemCode.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "生产订单产出物料不能为空");
        }
        Quantities.requirePositive(plannedQuantity, "计划生产数量");
        if (materials == null || materials.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID, "生产订单至少需要一条用料");
        }
        for (ProductionMaterial material : materials) {
            material.validate();
        }
        if (planStartDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "生产订单计划开工日期不能为空");
        }
        if (planEndDate != null && planEndDate.isBefore(planStartDate)) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "计划完工日期不能早于计划开工日期");
        }
    }

    /**
     * 下达生产订单。
     */
    public void release() {
        if (!MfgStatus.DRAFT.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_STATE_CONFLICT, "只有草稿生产订单可以下达");
        }
        status = MfgStatus.RELEASED;
    }

    /**
     * 记录 MES 下发成功。
     *
     * @param workOrderCode MES 工单编码，可为空
     * @param syncedAt      下发时间
     */
    public void markMesPushed(String workOrderCode, LocalDateTime syncedAt) {
        mesSyncStatus = MesStatus.PUSHED;
        mesWorkOrderCode = workOrderCode;
        mesSyncedAt = syncedAt;
        mesLastError = null;
    }

    /**
     * 记录 MES 下发失败。
     *
     * @param error 失败摘要
     */
    public void markMesFailed(String error) {
        mesSyncStatus = MesStatus.FAILED;
        mesLastError = error;
    }

    /**
     * 领料：校验状态与数量后累加用料已领数量。
     *
     * @param material 用料
     * @param quantity 本次领料数量
     */
    public void issue(ProductionMaterial material, BigDecimal quantity) {
        if (!MfgStatus.RELEASED.equals(status) && !MfgStatus.IN_PROGRESS.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_STATE_CONFLICT, "只有已下达或生产中的订单可以领料");
        }
        material.issue(quantity);
        status = MfgStatus.IN_PROGRESS;
    }

    /**
     * 累加领料成本。
     *
     * @param cost 本次领料成本
     */
    public void accumulateMaterialCost(BigDecimal cost) {
        BigDecimal value = Amounts.nonNegative(cost, "领料成本");
        totalMaterialCost = (totalMaterialCost == null ? BigDecimal.ZERO : totalMaterialCost).add(value);
    }

    /**
     * 工序报工：累计合格/报废数量，非首道工序累计合格量不得超过前道。
     *
     * @param stepNo           工序序号
     * @param qualifiedQuantity 本次合格数量
     * @param scrappedQuantity  本次报废数量
     * @param reportedAt       报工时间
     */
    public void reportOperation(Integer stepNo, BigDecimal qualifiedQuantity, BigDecimal scrappedQuantity,
                                LocalDateTime reportedAt) {
        if (!MfgStatus.RELEASED.equals(status) && !MfgStatus.IN_PROGRESS.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_STATE_CONFLICT, "只有已下达或生产中的订单可以报工");
        }
        if (operations == null || operations.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.OPERATION_INVALID, "订单没有工序，无法报工");
        }
        int index = indexOfStep(stepNo);
        ProductionOperation operation = operations.get(index);
        BigDecimal qualified = qualifiedQuantity == null
                ? BigDecimal.ZERO : Quantities.nonNegative(qualifiedQuantity, "报工合格数量");
        BigDecimal prospective = (operation.getQualifiedQuantity() == null
                ? BigDecimal.ZERO : operation.getQualifiedQuantity()).add(qualified);
        if (index > 0) {
            ProductionOperation previous = operations.get(index - 1);
            BigDecimal previousQualified = previous.getQualifiedQuantity() == null
                    ? BigDecimal.ZERO : previous.getQualifiedQuantity();
            if (prospective.compareTo(previousQualified) > 0) {
                throw ErpErrorCode.exception(ErpErrorCode.OPERATION_INVALID,
                        "工序 " + stepNo + " 累计合格数量不能超过前道工序");
            }
        }
        operation.report(qualified, scrappedQuantity, reportedAt);
        status = MfgStatus.IN_PROGRESS;
    }

    /**
     * 退料：按已领成本冲减，状态须为已下达或生产中。
     *
     * @param material 用料
     * @param quantity 退料数量
     * @return 冲减金额
     */
    public BigDecimal returnMaterial(ProductionMaterial material, BigDecimal quantity) {
        requireAdjustableState("退料");
        return reduceMaterialCost(material.reduceIssued(quantity, ErpErrorCode.MATERIAL_RETURN_EXCEEDED));
    }

    /**
     * 报废：按已领成本冲减且不回库存，状态须为已下达或生产中。
     *
     * @param material 用料
     * @param quantity 报废数量
     * @return 冲减金额
     */
    public BigDecimal scrapMaterial(ProductionMaterial material, BigDecimal quantity) {
        requireAdjustableState("报废");
        return reduceMaterialCost(material.reduceIssued(quantity, ErpErrorCode.MATERIAL_SCRAP_EXCEEDED));
    }

    /**
     * 完工入库：返回本次入库金额，最后一批结转剩余材料成本。
     *
     * @param quantity 本次完工数量
     * @param completeDate 实际完工日期
     * @return 本次入库金额
     */
    public BigDecimal complete(BigDecimal quantity, LocalDate completeDate) {
        if (!MfgStatus.IN_PROGRESS.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_STATE_CONFLICT, "只有生产中的订单可以完工入库");
        }
        requireOperationsQualified(quantity);
        requireMaterialsIssued();
        Quantities.requirePositive(quantity, "完工数量");
        BigDecimal done = completedQuantity == null ? BigDecimal.ZERO : completedQuantity;
        BigDecimal target = done.add(quantity);
        if (target.compareTo(plannedQuantity) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.PRODUCTION_COMPLETE_EXCEEDED);
        }
        BigDecimal materialCost = totalMaterialCost == null ? BigDecimal.ZERO : totalMaterialCost;
        BigDecimal unitCost = materialCost.divide(plannedQuantity, COST_SCALE, RoundingMode.HALF_UP);
        BigDecimal amount = unitCost.multiply(quantity).setScale(Amounts.SCALE, RoundingMode.HALF_UP);
        if (target.compareTo(plannedQuantity) == 0) {
            BigDecimal capitalized = completedCost == null ? BigDecimal.ZERO : completedCost;
            BigDecimal remainder = materialCost.subtract(capitalized);
            amount = remainder.signum() < 0 ? BigDecimal.ZERO : remainder;
        }
        completedQuantity = target;
        completedCost = (completedCost == null ? BigDecimal.ZERO : completedCost).add(amount);
        this.completeDate = completeDate;
        if (target.compareTo(plannedQuantity) == 0) {
            status = MfgStatus.COMPLETED;
        }
        return amount;
    }

    /**
     * 结案：仅已完工订单可结案。
     */
    public void close() {
        if (!MfgStatus.COMPLETED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_STATE_CONFLICT, "只有已完工生产订单可以结案");
        }
        status = MfgStatus.CLOSED;
    }

    /**
     * 取消生产订单：仅草稿或已下达可取消。
     */
    public void cancel() {
        if (!MfgStatus.DRAFT.equals(status) && !MfgStatus.RELEASED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_STATE_CONFLICT, "已领料或已完工的生产订单不能取消");
        }
        status = MfgStatus.CANCELLED;
    }

    /**
     * 校验全部非倒冲用料均已领完。
     */
    private void requireMaterialsIssued() {
        for (ProductionMaterial material : materials) {
            if (Boolean.TRUE.equals(material.getBackflush())) {
                continue;
            }
            if (!material.fullyIssued()) {
                throw ErpErrorCode.exception(ErpErrorCode.MATERIAL_NOT_ISSUED,
                        "物料 " + material.getItemCode() + " 尚未领完，禁止完工入库");
            }
        }
    }

    /**
     * 存在工序时，本批完工后累计完工量不得超过末道工序累计合格量。
     *
     * @param quantity 本批完工数量
     */
    private void requireOperationsQualified(BigDecimal quantity) {
        if (operations == null || operations.isEmpty()) {
            return;
        }
        ProductionOperation last = operations.stream()
                .max(Comparator.comparing(ProductionOperation::getStepNo))
                .orElse(null);
        BigDecimal qualified = last == null || last.getQualifiedQuantity() == null
                ? BigDecimal.ZERO : last.getQualifiedQuantity();
        BigDecimal done = completedQuantity == null ? BigDecimal.ZERO : completedQuantity;
        if (done.add(quantity).compareTo(qualified) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.OPERATION_INVALID,
                    "本批完工数量超过末道工序累计合格数量");
        }
    }

    /**
     * 按工序号查找下标。
     *
     * @param stepNo 工序号
     * @return 下标
     */
    private int indexOfStep(Integer stepNo) {
        for (int index = 0; index < operations.size(); index++) {
            if (operations.get(index).getStepNo().equals(stepNo)) {
                return index;
            }
        }
        throw ErpErrorCode.exception(ErpErrorCode.PRODUCTION_OPERATION_NOT_FOUND);
    }

    /**
     * 校验订单可执行退料 / 报废。
     *
     * @param action 动作名称
     */
    private void requireAdjustableState(String action) {
        if (!MfgStatus.RELEASED.equals(status) && !MfgStatus.IN_PROGRESS.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.MFG_STATE_CONFLICT,
                    "只有已下达或生产中的订单可以" + action);
        }
    }

    /**
     * 冲减订单累计材料成本。
     *
     * @param amount 冲减金额
     * @return 冲减金额
     */
    private BigDecimal reduceMaterialCost(BigDecimal amount) {
        BigDecimal cost = totalMaterialCost == null ? BigDecimal.ZERO : totalMaterialCost;
        BigDecimal remaining = cost.subtract(amount);
        totalMaterialCost = remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
        return amount;
    }
}

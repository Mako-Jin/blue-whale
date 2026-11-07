package com.bluewhale.erp.mfg.application.view;

import com.bluewhale.erp.mfg.domain.model.ProductionMaterial;
import com.bluewhale.erp.mfg.domain.model.ProductionOperation;
import com.bluewhale.erp.mfg.domain.model.ProductionOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 生产订单视图。
 *
 * @author BlueWhale
 */
@Data
public class ProductionOrderView {

    /** 主键 */
    private Long id;

    /** 生产订单号 */
    private String orderNo;

    /** 产出物料主键 */
    private Long itemId;

    /** 产出物料编码 */
    private String itemCode;

    /** 产出物料名称 */
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

    /** 领料仓库名称 */
    private String issueWarehouseName;

    /** 完工入库仓库 */
    private Long targetWarehouseId;

    /** 完工入库仓库名称 */
    private String targetWarehouseName;

    /** 计划开工日期 */
    private LocalDate planStartDate;

    /** 计划完工日期 */
    private LocalDate planEndDate;

    /** 实际完工日期 */
    private LocalDate completeDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 工艺路线编号快照 */
    private String routingNo;

    /** 工艺路线版本快照 */
    private String routingVersion;

    /** 工艺路线状态：NONE / SNAPSHOT / FAILED */
    private String routingStatus;

    /** 用料 */
    private List<MaterialView> materials;

    /** 工序 */
    private List<OperationView> operations;

    /**
     * 由领域对象构建视图。
     *
     * @param order 生产订单
     * @return 视图
     */
    public static ProductionOrderView of(ProductionOrder order) {
        ProductionOrderView view = new ProductionOrderView();
        view.setId(order.getId());
        view.setOrderNo(order.getOrderNo());
        view.setItemId(order.getItemId());
        view.setItemCode(order.getItemCode());
        view.setItemName(order.getItemName());
        view.setPlannedQuantity(order.getPlannedQuantity());
        view.setCompletedQuantity(order.getCompletedQuantity());
        view.setCompletedCost(order.getCompletedCost());
        view.setTotalMaterialCost(order.getTotalMaterialCost());
        view.setIssueWarehouseId(order.getIssueWarehouseId());
        view.setIssueWarehouseName(order.getIssueWarehouseName());
        view.setTargetWarehouseId(order.getTargetWarehouseId());
        view.setTargetWarehouseName(order.getTargetWarehouseName());
        view.setPlanStartDate(order.getPlanStartDate());
        view.setPlanEndDate(order.getPlanEndDate());
        view.setCompleteDate(order.getCompleteDate());
        view.setStatus(order.getStatus());
        view.setRemark(order.getRemark());
        view.setRoutingNo(order.getRoutingNo());
        view.setRoutingVersion(order.getRoutingVersion());
        view.setRoutingStatus(order.getRoutingStatus());
        if (order.getMaterials() != null) {
            view.setMaterials(order.getMaterials().stream().map(MaterialView::of).toList());
        }
        if (order.getOperations() != null) {
            view.setOperations(order.getOperations().stream().map(OperationView::of).toList());
        }
        return view;
    }

    /**
     * 生产工序视图。
     */
    @Data
    public static class OperationView {

        /** 工序序号 */
        private Integer stepNo;

        /** 工序名称 */
        private String name;

        /** 工作中心编码 */
        private String workCenterCode;

        /** 单件标准工时 */
        private BigDecimal standardHours;

        /** 准备工时 */
        private BigDecimal setupHours;

        /** 计划数量 */
        private BigDecimal plannedQuantity;

        /** 累计合格数量 */
        private BigDecimal qualifiedQuantity;

        /** 累计报废数量 */
        private BigDecimal scrappedQuantity;

        /** 状态 */
        private String status;

        /**
         * 由领域对象构建视图。
         *
         * @param operation 工序
         * @return 视图
         */
        public static OperationView of(ProductionOperation operation) {
            OperationView view = new OperationView();
            view.setStepNo(operation.getStepNo());
            view.setName(operation.getName());
            view.setWorkCenterCode(operation.getWorkCenterCode());
            view.setStandardHours(operation.getStandardHours());
            view.setSetupHours(operation.getSetupHours());
            view.setPlannedQuantity(operation.getPlannedQuantity());
            view.setQualifiedQuantity(operation.getQualifiedQuantity());
            view.setScrappedQuantity(operation.getScrappedQuantity());
            view.setStatus(operation.getStatus());
            return view;
        }
    }

    /**
     * 生产用料视图。
     */
    @Data
    public static class MaterialView {

        /** 行号 */
        private Integer lineNo;

        /** 物料主键 */
        private Long itemId;

        /** 物料编码 */
        private String itemCode;

        /** 物料名称 */
        private String itemName;

        /** 计量单位 */
        private String uom;

        /** 需求数量 */
        private BigDecimal requiredQuantity;

        /** 已领数量 */
        private BigDecimal issuedQuantity;

        /** 累计已领成本 */
        private BigDecimal issuedCost;

        /** 是否倒冲领料 */
        private Boolean backflush;

        /**
         * 由领域对象构建视图。
         *
         * @param material 用料
         * @return 视图
         */
        public static MaterialView of(ProductionMaterial material) {
            MaterialView view = new MaterialView();
            view.setLineNo(material.getLineNo());
            view.setItemId(material.getItemId());
            view.setItemCode(material.getItemCode());
            view.setItemName(material.getItemName());
            view.setUom(material.getUom());
            view.setRequiredQuantity(material.getRequiredQuantity());
            view.setIssuedQuantity(material.getIssuedQuantity());
            view.setIssuedCost(material.getIssuedCost());
            view.setBackflush(material.getBackflush());
            return view;
        }
    }
}

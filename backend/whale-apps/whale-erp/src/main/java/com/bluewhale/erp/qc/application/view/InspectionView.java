package com.bluewhale.erp.qc.application.view;

import com.bluewhale.erp.qc.domain.model.InspectionItem;
import com.bluewhale.erp.qc.domain.model.InspectionOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 检验单视图。
 *
 * @author BlueWhale
 */
@Data
public class InspectionView {

    /** 主键 */
    private Long id;

    /** 检验单号 */
    private String orderNo;

    /** 检验类型 */
    private String inspectionType;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据号 */
    private String sourceNo;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 批次号 */
    private String lotNo;

    /** 送检数量 */
    private BigDecimal quantity;

    /** 已检验数量 */
    private BigDecimal inspectedQuantity;

    /** 供应商编码 */
    private String supplierCode;

    /** 状态 */
    private String status;

    /** 检验员 */
    private String inspector;

    /** 检验日期 */
    private LocalDate inspectDate;

    /** 量具主键 */
    private Long toolId;

    /** 量具编号 */
    private String toolNo;

    /** 不合格品单主键 */
    private Long ncrId;

    /** 备注 */
    private String remark;

    /** 检验项 */
    private List<ItemView> items;

    /**
     * 由领域对象构建视图。
     *
     * @param order 检验单
     * @return 视图
     */
    public static InspectionView of(InspectionOrder order) {
        InspectionView view = new InspectionView();
        view.setId(order.getId());
        view.setOrderNo(order.getOrderNo());
        view.setInspectionType(order.getInspectionType());
        view.setSourceType(order.getSourceType());
        view.setSourceNo(order.getSourceNo());
        view.setItemId(order.getItemId());
        view.setItemCode(order.getItemCode());
        view.setItemName(order.getItemName());
        view.setLotNo(order.getLotNo());
        view.setQuantity(order.getQuantity());
        view.setInspectedQuantity(order.getInspectedQuantity());
        view.setSupplierCode(order.getSupplierCode());
        view.setStatus(order.getStatus());
        view.setInspector(order.getInspector());
        view.setInspectDate(order.getInspectDate());
        view.setToolId(order.getToolId());
        view.setToolNo(order.getToolNo());
        view.setNcrId(order.getNcrId());
        view.setRemark(order.getRemark());
        if (order.getItems() != null) {
            view.setItems(order.getItems().stream().map(ItemView::of).toList());
        }
        return view;
    }

    /**
     * 检验项视图。
     */
    @Data
    public static class ItemView {

        /** 行号 */
        private Integer lineNo;

        /** 检验项名称 */
        private String itemName;

        /** 标准值 */
        private String standardValue;

        /** 实测值 */
        private String actualValue;

        /** 结论 */
        private String result;

        /** 备注 */
        private String remark;

        /**
         * 由领域对象构建视图。
         *
         * @param item 检验项
         * @return 视图
         */
        public static ItemView of(InspectionItem item) {
            ItemView view = new ItemView();
            view.setLineNo(item.getLineNo());
            view.setItemName(item.getItemName());
            view.setStandardValue(item.getStandardValue());
            view.setActualValue(item.getActualValue());
            view.setResult(item.getResult());
            view.setRemark(item.getRemark());
            return view;
        }
    }
}

package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.PurchaseRequisition;
import com.bluewhale.erp.scm.domain.model.PurchaseRequisitionLine;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购申请单视图。
 *
 * @author BlueWhale
 */
@Data
public class PurchaseRequisitionView {

    /** 主键 */
    private Long id;

    /** 申请单号 */
    private String docNo;

    /** 供应商主键 */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 申请日期 */
    private LocalDate docDate;

    /** 需求到货日期 */
    private LocalDate requiredDate;

    /** 仓库主键 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 申请总额 */
    private BigDecimal totalAmount;

    /** 转单生成的采购订单号 */
    private String convertedOrderNo;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<RequisitionLineView> lines;

    /**
     * 由领域对象构建视图。
     *
     * @param requisition 采购申请单
     * @return 视图
     */
    public static PurchaseRequisitionView of(PurchaseRequisition requisition) {
        PurchaseRequisitionView view = new PurchaseRequisitionView();
        view.setId(requisition.getId());
        view.setDocNo(requisition.getDocNo());
        view.setSupplierId(requisition.getSupplierId());
        view.setSupplierName(requisition.getSupplierName());
        view.setDocDate(requisition.getDocDate());
        view.setRequiredDate(requisition.getRequiredDate());
        view.setWarehouseId(requisition.getWarehouseId());
        view.setStatus(requisition.getStatus());
        view.setCurrency(requisition.getCurrency());
        view.setTotalAmount(requisition.getTotalAmount());
        view.setConvertedOrderNo(requisition.getConvertedOrderNo());
        view.setRemark(requisition.getRemark());
        if (requisition.getLines() != null) {
            view.setLines(requisition.getLines().stream()
                    .map(PurchaseRequisitionView::toView).toList());
        }
        return view;
    }

    /**
     * 分录转视图。
     *
     * @param line 分录
     * @return 视图
     */
    private static RequisitionLineView toView(PurchaseRequisitionLine line) {
        RequisitionLineView view = new RequisitionLineView();
        view.setLineNo(line.getLineNo());
        view.setItemId(line.getItemId());
        view.setItemCode(line.getItemCode());
        view.setItemName(line.getItemName());
        view.setUom(line.getUom());
        view.setQuantity(line.getQuantity());
        view.setUnitPrice(line.getUnitPrice());
        view.setAmount(line.getAmount());
        return view;
    }
}

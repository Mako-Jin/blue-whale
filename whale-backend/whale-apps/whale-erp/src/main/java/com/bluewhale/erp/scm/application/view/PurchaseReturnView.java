package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.PurchaseReturn;
import com.bluewhale.erp.scm.domain.model.PurchaseReturnLine;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购退货单视图。
 *
 * @author BlueWhale
 */
@Data
public class PurchaseReturnView {

    /** 主键 */
    private Long id;

    /** 退货单号 */
    private String docNo;

    /** 源采购订单主键 */
    private Long purchaseOrderId;

    /** 供应商主键 */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 退货日期 */
    private LocalDate docDate;

    /** 仓库主键 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 退货总额 */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<ReturnLineView> lines;

    /**
     * 由领域对象构建视图。
     *
     * @param purchaseReturn 采购退货单
     * @return 视图
     */
    public static PurchaseReturnView of(PurchaseReturn purchaseReturn) {
        PurchaseReturnView view = new PurchaseReturnView();
        view.setId(purchaseReturn.getId());
        view.setDocNo(purchaseReturn.getDocNo());
        view.setPurchaseOrderId(purchaseReturn.getPurchaseOrderId());
        view.setSupplierId(purchaseReturn.getSupplierId());
        view.setSupplierName(purchaseReturn.getSupplierName());
        view.setDocDate(purchaseReturn.getDocDate());
        view.setWarehouseId(purchaseReturn.getWarehouseId());
        view.setStatus(purchaseReturn.getStatus());
        view.setCurrency(purchaseReturn.getCurrency());
        view.setTotalAmount(purchaseReturn.getTotalAmount());
        view.setRemark(purchaseReturn.getRemark());
        if (purchaseReturn.getLines() != null) {
            view.setLines(purchaseReturn.getLines().stream().map(PurchaseReturnView::toView).toList());
        }
        return view;
    }

    /**
     * 分录转视图。
     *
     * @param line 分录
     * @return 视图
     */
    private static ReturnLineView toView(PurchaseReturnLine line) {
        ReturnLineView view = new ReturnLineView();
        view.setLineNo(line.getLineNo());
        view.setOrderLineNo(line.getOrderLineNo());
        view.setItemId(line.getItemId());
        view.setItemCode(line.getItemCode());
        view.setItemName(line.getItemName());
        view.setUom(line.getUom());
        view.setQuantity(line.getQuantity());
        view.setUnitPrice(line.getUnitPrice());
        view.setAmount(line.getAmount());
        view.setUnitCost(line.getUnitCost());
        view.setCostAmount(line.getCostAmount());
        return view;
    }
}

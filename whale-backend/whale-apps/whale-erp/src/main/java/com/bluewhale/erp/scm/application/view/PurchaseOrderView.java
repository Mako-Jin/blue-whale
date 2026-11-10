package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.PurchaseOrder;
import com.bluewhale.erp.scm.domain.model.PurchaseOrderLine;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购订单视图。
 *
 * @author BlueWhale
 */
@Data
public class PurchaseOrderView {

    /** 主键 */
    private Long id;

    /** 订单号 */
    private String docNo;

    /** 供应商主键 */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 单据日期 */
    private LocalDate docDate;

    /** 预计到货日期 */
    private LocalDate expectedDate;

    /** 仓库主键 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 已收货金额 */
    private BigDecimal receivedAmount;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<LineView> lines;

    /**
     * 由领域对象构建视图。
     *
     * @param order 采购订单
     * @return 视图
     */
    public static PurchaseOrderView of(PurchaseOrder order) {
        PurchaseOrderView view = new PurchaseOrderView();
        view.setId(order.getId());
        view.setDocNo(order.getDocNo());
        view.setSupplierId(order.getSupplierId());
        view.setSupplierName(order.getSupplierName());
        view.setDocDate(order.getDocDate());
        view.setExpectedDate(order.getExpectedDate());
        view.setWarehouseId(order.getWarehouseId());
        view.setStatus(order.getStatus());
        view.setCurrency(order.getCurrency());
        view.setTotalAmount(order.getTotalAmount());
        view.setReceivedAmount(order.getReceivedAmount());
        view.setRemark(order.getRemark());
        if (order.getLines() != null) {
            view.setLines(order.getLines().stream().map(LineView::of).toList());
        }
        return view;
    }

    /**
     * 采购订单分录视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class LineView {

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

        /** 订单数量 */
        private BigDecimal quantity;

        /** 已收数量 */
        private BigDecimal receivedQuantity;

        /** 单价 */
        private BigDecimal unitPrice;

        /** 行金额 */
        private BigDecimal amount;

        /**
         * 由领域对象构建视图。
         *
         * @param line 分录
         * @return 视图
         */
        public static LineView of(PurchaseOrderLine line) {
            LineView view = new LineView();
            view.setLineNo(line.getLineNo());
            view.setItemId(line.getItemId());
            view.setItemCode(line.getItemCode());
            view.setItemName(line.getItemName());
            view.setUom(line.getUom());
            view.setQuantity(line.getQuantity());
            view.setReceivedQuantity(line.getReceivedQuantity());
            view.setUnitPrice(line.getUnitPrice());
            view.setAmount(line.getAmount());
            return view;
        }
    }
}

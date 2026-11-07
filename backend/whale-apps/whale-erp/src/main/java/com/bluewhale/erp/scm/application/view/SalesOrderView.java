package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.SalesOrder;
import com.bluewhale.erp.scm.domain.model.SalesOrderLine;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售订单视图。
 *
 * @author BlueWhale
 */
@Data
public class SalesOrderView {

    /** 主键 */
    private Long id;

    /** 订单号 */
    private String docNo;

    /** 客户主键 */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 单据日期 */
    private LocalDate docDate;

    /** 计划发货日期 */
    private LocalDate deliveryDate;

    /** 仓库主键 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 已发货金额 */
    private BigDecimal shippedAmount;

    /** 备注 */
    private String remark;

    /** 来源系统类型 */
    private String sourceType;

    /** 来源单号 */
    private String sourceId;

    /** CRM 商务订单号 */
    private String crmOrderNo;

    /** 分录 */
    private List<LineView> lines;

    /**
     * 由领域对象构建视图。
     *
     * @param order 销售订单
     * @return 视图
     */
    public static SalesOrderView of(SalesOrder order) {
        SalesOrderView view = new SalesOrderView();
        view.setId(order.getId());
        view.setDocNo(order.getDocNo());
        view.setCustomerId(order.getCustomerId());
        view.setCustomerName(order.getCustomerName());
        view.setDocDate(order.getDocDate());
        view.setDeliveryDate(order.getDeliveryDate());
        view.setWarehouseId(order.getWarehouseId());
        view.setStatus(order.getStatus());
        view.setCurrency(order.getCurrency());
        view.setTotalAmount(order.getTotalAmount());
        view.setShippedAmount(order.getShippedAmount());
        view.setRemark(order.getRemark());
        view.setSourceType(order.getSourceType());
        view.setSourceId(order.getSourceId());
        view.setCrmOrderNo(order.getCrmOrderNo());
        if (order.getLines() != null) {
            view.setLines(order.getLines().stream().map(LineView::of).toList());
        }
        return view;
    }

    /**
     * 销售订单分录视图。
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

        /** 已发数量 */
        private BigDecimal shippedQuantity;

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
        public static LineView of(SalesOrderLine line) {
            LineView view = new LineView();
            view.setLineNo(line.getLineNo());
            view.setItemId(line.getItemId());
            view.setItemCode(line.getItemCode());
            view.setItemName(line.getItemName());
            view.setUom(line.getUom());
            view.setQuantity(line.getQuantity());
            view.setShippedQuantity(line.getShippedQuantity());
            view.setUnitPrice(line.getUnitPrice());
            view.setAmount(line.getAmount());
            return view;
        }
    }
}

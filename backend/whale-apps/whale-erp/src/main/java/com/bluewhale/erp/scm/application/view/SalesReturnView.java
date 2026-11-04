package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.SalesReturn;
import com.bluewhale.erp.scm.domain.model.SalesReturnLine;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售退货单视图。
 *
 * @author BlueWhale
 */
@Data
public class SalesReturnView {

    /** 主键 */
    private Long id;

    /** 退货单号 */
    private String docNo;

    /** 源销售订单主键 */
    private Long salesOrderId;

    /** 客户主键 */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

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
     * @param salesReturn 销售退货单
     * @return 视图
     */
    public static SalesReturnView of(SalesReturn salesReturn) {
        SalesReturnView view = new SalesReturnView();
        view.setId(salesReturn.getId());
        view.setDocNo(salesReturn.getDocNo());
        view.setSalesOrderId(salesReturn.getSalesOrderId());
        view.setCustomerId(salesReturn.getCustomerId());
        view.setCustomerName(salesReturn.getCustomerName());
        view.setDocDate(salesReturn.getDocDate());
        view.setWarehouseId(salesReturn.getWarehouseId());
        view.setStatus(salesReturn.getStatus());
        view.setCurrency(salesReturn.getCurrency());
        view.setTotalAmount(salesReturn.getTotalAmount());
        view.setRemark(salesReturn.getRemark());
        if (salesReturn.getLines() != null) {
            view.setLines(salesReturn.getLines().stream().map(SalesReturnView::toView).toList());
        }
        return view;
    }

    /**
     * 分录转视图。
     *
     * @param line 分录
     * @return 视图
     */
    private static ReturnLineView toView(SalesReturnLine line) {
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

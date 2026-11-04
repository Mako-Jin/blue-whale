package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.SalesQuotation;
import com.bluewhale.erp.scm.domain.model.SalesQuotationLine;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售报价单视图。
 *
 * @author BlueWhale
 */
@Data
public class SalesQuotationView {

    /** 主键 */
    private Long id;

    /** 报价单号 */
    private String docNo;

    /** 客户主键 */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 报价日期 */
    private LocalDate docDate;

    /** 报价有效期至 */
    private LocalDate validUntil;

    /** 仓库主键 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 报价总额 */
    private BigDecimal totalAmount;

    /** 转单生成的销售订单号 */
    private String convertedOrderNo;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<QuotationLineView> lines;

    /**
     * 由领域对象构建视图。
     *
     * @param quotation 销售报价单
     * @return 视图
     */
    public static SalesQuotationView of(SalesQuotation quotation) {
        SalesQuotationView view = new SalesQuotationView();
        view.setId(quotation.getId());
        view.setDocNo(quotation.getDocNo());
        view.setCustomerId(quotation.getCustomerId());
        view.setCustomerName(quotation.getCustomerName());
        view.setDocDate(quotation.getDocDate());
        view.setValidUntil(quotation.getValidUntil());
        view.setWarehouseId(quotation.getWarehouseId());
        view.setStatus(quotation.getStatus());
        view.setCurrency(quotation.getCurrency());
        view.setTotalAmount(quotation.getTotalAmount());
        view.setConvertedOrderNo(quotation.getConvertedOrderNo());
        view.setRemark(quotation.getRemark());
        if (quotation.getLines() != null) {
            view.setLines(quotation.getLines().stream().map(SalesQuotationView::toView).toList());
        }
        return view;
    }

    /**
     * 分录转视图。
     *
     * @param line 分录
     * @return 视图
     */
    private static QuotationLineView toView(SalesQuotationLine line) {
        QuotationLineView view = new QuotationLineView();
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

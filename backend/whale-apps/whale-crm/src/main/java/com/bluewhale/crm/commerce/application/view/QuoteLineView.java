package com.bluewhale.crm.commerce.application.view;

import com.bluewhale.crm.commerce.domain.model.QuoteLine;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 报价行视图。
 *
 * @author BlueWhale
 */
@Data
public class QuoteLineView {

    /** 主键 */
    private Long id;

    /** 行号 */
    private Integer lineNo;

    /** 产品 / 服务名称 */
    private String productName;

    /** 规格 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 行折扣率 */
    private BigDecimal discountRate;

    /** 税率 */
    private BigDecimal taxRate;

    /** 行金额 */
    private BigDecimal lineAmount;

    /** 行备注 */
    private String remark;

    /**
     * 领域模型转视图。
     *
     * @param line 报价行
     * @return 视图
     */
    public static QuoteLineView from(QuoteLine line) {
        QuoteLineView view = new QuoteLineView();
        view.setId(line.getId());
        view.setLineNo(line.getLineNo());
        view.setProductName(line.getProductName());
        view.setSpecification(line.getSpecification());
        view.setUnit(line.getUnit());
        view.setQuantity(line.getQuantity());
        view.setUnitPrice(line.getUnitPrice());
        view.setDiscountRate(line.getDiscountRate());
        view.setTaxRate(line.getTaxRate());
        view.setLineAmount(line.getLineAmount());
        view.setRemark(line.getRemark());
        return view;
    }
}

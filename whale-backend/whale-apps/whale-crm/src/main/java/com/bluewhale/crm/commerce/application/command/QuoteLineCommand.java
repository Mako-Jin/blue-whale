package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 报价行命令。
 *
 * @author BlueWhale
 */
@Data
public class QuoteLineCommand {

    /** 产品 / 服务名称 */
    @NotBlank(message = "{validation.crm.quote-line.product-name-required}")
    @Size(max = 128, message = "{validation.crm.quote-line.product-name-size}")
    private String productName;

    /** 规格 */
    @Size(max = 128, message = "{validation.crm.quote-line.specification-size}")
    private String specification;

    /** 单位 */
    @Size(max = 16, message = "{validation.crm.quote-line.unit-size}")
    private String unit;

    /** 数量 */
    @NotNull(message = "{validation.crm.quote-line.quantity-required}")
    @DecimalMin(value = "0.0001", message = "{validation.crm.quote-line.quantity-min}")
    private BigDecimal quantity;

    /** 单价 */
    @NotNull(message = "{validation.crm.quote-line.unit-price-required}")
    @DecimalMin(value = "0", message = "{validation.crm.quote-line.unit-price-min}")
    private BigDecimal unitPrice;

    /** 行折扣率 */
    @DecimalMin(value = "0", message = "{validation.crm.quote-line.discount-rate-min}")
    @DecimalMax(value = "100", message = "{validation.crm.quote-line.discount-rate-max}")
    private BigDecimal discountRate = BigDecimal.ZERO;

    /** 税率 */
    @DecimalMin(value = "0", message = "{validation.crm.quote-line.tax-rate-min}")
    @DecimalMax(value = "100", message = "{validation.crm.quote-line.tax-rate-max}")
    private BigDecimal taxRate = new BigDecimal("13");

    /** 行备注 */
    @Size(max = 255, message = "{validation.crm.quote-line.remark-size}")
    private String remark;
}

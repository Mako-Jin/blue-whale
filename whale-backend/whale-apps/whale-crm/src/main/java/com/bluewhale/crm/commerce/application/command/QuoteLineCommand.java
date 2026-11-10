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
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 128, message = "产品名称最多 128 个字符")
    private String productName;

    /** 规格 */
    @Size(max = 128, message = "规格最多 128 个字符")
    private String specification;

    /** 单位 */
    @Size(max = 16, message = "单位最多 16 个字符")
    private String unit;

    /** 数量 */
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.0001", message = "数量必须大于 0")
    private BigDecimal quantity;

    /** 单价 */
    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0", message = "单价不能为负数")
    private BigDecimal unitPrice;

    /** 行折扣率 */
    @DecimalMin(value = "0", message = "行折扣率不能为负数")
    @DecimalMax(value = "100", message = "行折扣率不能超过 100")
    private BigDecimal discountRate = BigDecimal.ZERO;

    /** 税率 */
    @DecimalMin(value = "0", message = "税率不能为负数")
    @DecimalMax(value = "100", message = "税率不能超过 100")
    private BigDecimal taxRate = new BigDecimal("13");

    /** 行备注 */
    @Size(max = 255, message = "行备注最多 255 个字符")
    private String remark;
}

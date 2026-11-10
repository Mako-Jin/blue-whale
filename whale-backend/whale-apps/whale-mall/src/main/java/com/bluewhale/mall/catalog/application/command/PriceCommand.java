package com.bluewhale.mall.catalog.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 价目命令。
 *
 * @author BlueWhale
 */
@Data
public class PriceCommand {

    /** 价目类型：RETAIL / B2B / POINTS */
    @NotBlank(message = "价目类型不能为空")
    private String priceType;

    /** 价格（积分价必须为整数） */
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;
}

package com.bluewhale.erp.scm.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增安全库存命令。
 *
 * @author BlueWhale
 */
@Data
public class SafetyStockCreateCommand {

    /** 仓库主键 */
    @NotNull
    private Long warehouseId;

    /** 物料编码 */
    @NotBlank
    private String itemCode;

    /** 安全库存量 */
    @NotNull
    @DecimalMin("0")
    @Digits(integer = 14, fraction = 4)
    private BigDecimal safetyQty;

    /** 最高库存量（空表示不设上限） */
    @DecimalMin("0")
    @Digits(integer = 14, fraction = 4)
    private BigDecimal maxQty;

    /** 备注 */
    private String remark;
}

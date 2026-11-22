package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 生产工序报工命令。
 *
 * @author BlueWhale
 */
@Data
public class ProductionOperationReportCommand {

    /** 本次合格数量 */
    @DecimalMin(value = "0", message = "{validation.erp.production-operation-report.qualified-quantity-min}")
    private BigDecimal qualifiedQuantity;

    /** 本次报废数量 */
    @DecimalMin(value = "0", message = "{validation.erp.production-operation-report.scrapped-quantity-min}")
    private BigDecimal scrappedQuantity;
}

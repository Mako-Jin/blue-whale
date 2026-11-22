package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MRP 运算命令：一条或多条需求行。
 *
 * @author BlueWhale
 */
@Data
public class MrpRunCommand {

    /** 备注 */
    private String remark;

    /** 需求行 */
    @Valid
    @NotEmpty(message = "{validation.erp.mrp-run.lines-required}")
    private List<Line> lines = new ArrayList<>();

    /**
     * 需求行。
     */
    @Data
    public static class Line {

        /** 需求物料编码（须与 BOM 快照产品编码一致） */
        @NotBlank(message = "{validation.erp.mfg-line.item-code-required}")
        private String itemCode;

        /** PDM BOM 编号 */
        @NotBlank(message = "{validation.erp.line.bom-no-required}")
        private String bomNo;

        /** PDM BOM 版本 */
        @NotBlank(message = "{validation.erp.line.version-no-required}")
        private String versionNo;

        /** 需求数量 */
        @NotNull(message = "{validation.erp.mfg-line.quantity-required}")
        @DecimalMin(value = "0.0001", message = "{validation.erp.line.quantity-min}")
        private BigDecimal quantity;

        /** 需求日期 */
        @NotNull(message = "{validation.erp.line.due-date-required}")
        private LocalDate dueDate;
    }
}

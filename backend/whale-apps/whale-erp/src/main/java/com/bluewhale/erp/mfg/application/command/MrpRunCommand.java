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
    @NotEmpty(message = "至少需要一条需求行")
    private List<Line> lines = new ArrayList<>();

    /**
     * 需求行。
     */
    @Data
    public static class Line {

        /** 需求物料编码（须与 BOM 快照产品编码一致） */
        @NotBlank(message = "需求物料编码不能为空")
        private String itemCode;

        /** PDM BOM 编号 */
        @NotBlank(message = "BOM 编号不能为空")
        private String bomNo;

        /** PDM BOM 版本 */
        @NotBlank(message = "BOM 版本不能为空")
        private String versionNo;

        /** 需求数量 */
        @NotNull(message = "需求数量不能为空")
        @DecimalMin(value = "0.0001", message = "需求数量必须大于 0")
        private BigDecimal quantity;

        /** 需求日期 */
        @NotNull(message = "需求日期不能为空")
        private LocalDate dueDate;
    }
}

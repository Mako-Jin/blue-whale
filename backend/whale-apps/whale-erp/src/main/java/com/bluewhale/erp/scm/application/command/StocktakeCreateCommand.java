package com.bluewhale.erp.scm.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新增库存盘点单命令。
 *
 * @author BlueWhale
 */
@Data
public class StocktakeCreateCommand {

    /** 盘点日期 */
    @NotNull
    private LocalDate stocktakeDate;

    /** 盘点仓库 */
    @NotNull
    private Long warehouseId;

    /** 备注 */
    private String remark;

    /** 分录 */
    @Valid
    @NotEmpty
    private List<Line> lines;

    /**
     * 盘点分录入参。
     */
    @Data
    public static class Line {

        /** 物料编码 */
        @NotBlank
        private String itemCode;

        /** 实盘数量（允许为零） */
        @NotNull
        private BigDecimal countedQuantity;

        /** 计量单位 */
        private String uom;
    }
}

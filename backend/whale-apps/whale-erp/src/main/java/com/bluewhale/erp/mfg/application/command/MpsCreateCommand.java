package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增主生产计划命令。
 *
 * @author BlueWhale
 */
@Data
public class MpsCreateCommand {

    /** 计划期间（YYYY-MM） */
    @NotBlank
    private String period;

    /** 备注 */
    private String remark;

    /** 计划分录 */
    @Valid
    @NotEmpty
    private List<Line> lines;

    /**
     * 计划分录入参。
     */
    @Data
    public static class Line {

        /** 物料编码 */
        @NotBlank
        private String itemCode;

        /** 计划数量 */
        @NotNull
        private BigDecimal plannedQuantity;

        /** 计量单位 */
        private String uom;
    }
}

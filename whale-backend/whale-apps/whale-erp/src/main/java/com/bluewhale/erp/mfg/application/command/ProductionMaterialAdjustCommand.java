package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 生产用料退料 / 报废命令。
 *
 * @author BlueWhale
 */
@Data
public class ProductionMaterialAdjustCommand {

    /** 调整分录 */
    @Valid
    @NotEmpty
    private List<Line> lines;

    /**
     * 调整分录入参。
     */
    @Data
    public static class Line {

        /** 用料行号 */
        @NotNull
        private Integer lineNo;

        /** 调整数量 */
        @NotNull
        private BigDecimal quantity;

        /** 原因 */
        private String reason;
    }
}

package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 生产领料命令。
 *
 * @author BlueWhale
 */
@Data
public class ProductionIssueCommand {

    /** 领料分录 */
    @Valid
    @NotEmpty
    private List<Line> lines;

    /**
     * 领料分录入参。
     */
    @Data
    public static class Line {

        /** 用料行号 */
        @NotNull
        private Integer lineNo;

        /** 本次领料数量 */
        @NotNull
        private BigDecimal quantity;
    }
}

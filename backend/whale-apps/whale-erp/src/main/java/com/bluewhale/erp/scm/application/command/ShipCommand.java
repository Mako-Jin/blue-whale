package com.bluewhale.erp.scm.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售发货命令。
 *
 * @author BlueWhale
 */
@Data
public class ShipCommand {

    /** 发货日期 */
    @NotNull
    private LocalDate shipDate;

    /** 发货明细 */
    @Valid
    @NotEmpty
    private List<Line> lines = new ArrayList<>();

    /**
     * 发货行命令。
     *
     * @author BlueWhale
     */
    @Data
    public static class Line {

        /** 订单行号 */
        @NotNull
        private Integer lineNo;

        /** 本次发货数量 */
        @NotNull
        private BigDecimal quantity;
    }
}

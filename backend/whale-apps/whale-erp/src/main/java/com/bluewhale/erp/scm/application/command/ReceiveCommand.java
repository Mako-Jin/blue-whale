package com.bluewhale.erp.scm.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购收货命令。
 *
 * @author BlueWhale
 */
@Data
public class ReceiveCommand {

    /** 收货日期 */
    @NotNull
    private java.time.LocalDate receiveDate;

    /** 收货明细 */
    @Valid
    @NotEmpty
    private List<Line> lines = new ArrayList<>();

    /**
     * 收货行命令。
     *
     * @author BlueWhale
     */
    @Data
    public static class Line {

        /** 订单行号 */
        @NotNull
        private Integer lineNo;

        /** 本次收货数量 */
        @NotNull
        private BigDecimal quantity;
    }
}

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
 * 新增销售退货单命令。
 *
 * @author BlueWhale
 */
@Data
public class SalesReturnCreateCommand {

    /** 源销售订单主键 */
    @NotNull
    private Long salesOrderId;

    /** 退货日期 */
    @NotNull
    private LocalDate docDate;

    /** 备注 */
    private String remark;

    /** 分录 */
    @Valid
    @NotEmpty
    private List<Line> lines = new ArrayList<>();

    /**
     * 销售退货分录命令。
     *
     * @author BlueWhale
     */
    @Data
    public static class Line {

        /** 源订单行号 */
        @NotNull
        private Integer orderLineNo;

        /** 退货数量 */
        @NotNull
        private BigDecimal quantity;
    }
}

package com.bluewhale.erp.scm.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * CRM 内网销售退货创建命令。
 *
 * @author BlueWhale
 */
@Data
public class SalesReturnInternalCreateCommand {

    /** CRM 退换货申请单号（幂等键） */
    @NotBlank
    private String crmReturnNo;

    /** 源销售订单主键（与 erpOrderNo 二选一） */
    private Long salesOrderId;

    /** ERP 销售订单号（与 salesOrderId 二选一） */
    private String erpOrderNo;

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

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

    /** 指定单号（内网 CRM 下发固定单号；为空时自动生成） */
    private String docNo;

    /** CRM 退换货申请单号（幂等键） */
    private String crmReturnNo;

    /** 来源系统类型 */
    private String sourceType;

    /** 来源单号 */
    private String sourceId;

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

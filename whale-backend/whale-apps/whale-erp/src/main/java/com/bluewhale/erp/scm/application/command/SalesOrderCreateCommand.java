package com.bluewhale.erp.scm.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 新增销售订单命令。
 *
 * @author BlueWhale
 */
@Data
public class SalesOrderCreateCommand {

    /** 客户主键 */
    @NotNull
    private Long customerId;

    /** 单据日期 */
    @NotNull
    private LocalDate docDate;

    /** 计划发货日期 */
    private LocalDate deliveryDate;

    /** 发货仓库主键 */
    @NotNull
    private Long warehouseId;

    /** 币种 */
    private String currency;

    /** 备注 */
    private String remark;

    /** 指定订单号（为空时按日期自动生成） */
    private String docNo;

    /** 来源系统类型 */
    private String sourceType;

    /** 来源单号 */
    private String sourceId;

    /** CRM 商务订单号（幂等键） */
    private String crmOrderNo;

    /** 分录 */
    @Valid
    @NotEmpty
    private List<Line> lines = new ArrayList<>();

    /**
     * 销售订单分录命令。
     *
     * @author BlueWhale
     */
    @Data
    public static class Line {

        /** 物料编码 */
        @NotBlank
        private String itemCode;

        /** 数量 */
        @NotNull
        private BigDecimal quantity;

        /** 单价（为空时按客户售价兜底取价） */
        @Positive
        private BigDecimal unitPrice;

        /** 计量单位 */
        private String uom;
    }
}

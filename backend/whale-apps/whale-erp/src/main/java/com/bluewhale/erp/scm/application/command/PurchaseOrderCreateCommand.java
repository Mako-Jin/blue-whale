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
 * 新增采购订单命令。
 *
 * @author BlueWhale
 */
@Data
public class PurchaseOrderCreateCommand {

    /** 订单号（为空时自动生成；内网幂等创建时由上游指定） */
    private String docNo;

    /** 供应商主键 */
    @NotNull
    private Long supplierId;

    /** 单据日期 */
    @NotNull
    private LocalDate docDate;

    /** 预计到货日期 */
    private LocalDate expectedDate;

    /** 收货仓库主键 */
    @NotNull
    private Long warehouseId;

    /** 币种 */
    private String currency;

    /** 备注 */
    private String remark;

    /** 分录 */
    @Valid
    @NotEmpty
    private List<Line> lines = new ArrayList<>();

    /**
     * 采购订单分录命令。
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

        /** 单价（为空时按供应商采购价兜底取价） */
        @Positive
        private BigDecimal unitPrice;

        /** 计量单位 */
        private String uom;
    }
}

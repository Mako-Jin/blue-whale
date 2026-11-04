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
 * 新增采购申请单命令。
 *
 * @author BlueWhale
 */
@Data
public class PurchaseRequisitionCreateCommand {

    /** 供应商主键 */
    @NotNull
    private Long supplierId;

    /** 申请日期 */
    @NotNull
    private LocalDate docDate;

    /** 需求到货日期 */
    private LocalDate requiredDate;

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
     * 采购申请分录命令。
     *
     * @author BlueWhale
     */
    @Data
    public static class Line {

        /** 物料编码 */
        @NotBlank
        private String itemCode;

        /** 申请数量 */
        @NotNull
        private BigDecimal quantity;

        /** 预估单价 */
        @NotNull
        private BigDecimal unitPrice;

        /** 计量单位 */
        private String uom;
    }
}

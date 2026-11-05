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
 * 新增销售报价单命令。
 *
 * @author BlueWhale
 */
@Data
public class SalesQuotationCreateCommand {

    /** 客户主键 */
    @NotNull
    private Long customerId;

    /** 报价日期 */
    @NotNull
    private LocalDate docDate;

    /** 报价有效期至 */
    private LocalDate validUntil;

    /** 发货仓库主键 */
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
     * 销售报价分录命令。
     *
     * @author BlueWhale
     */
    @Data
    public static class Line {

        /** 物料编码 */
        @NotBlank
        private String itemCode;

        /** 报价数量 */
        @NotNull
        private BigDecimal quantity;

        /** 报价单价（为空时按客户售价兜底取价） */
        @Positive
        private BigDecimal unitPrice;

        /** 计量单位 */
        private String uom;
    }
}

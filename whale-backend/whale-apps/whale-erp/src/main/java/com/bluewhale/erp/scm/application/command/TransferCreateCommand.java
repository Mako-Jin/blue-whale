package com.bluewhale.erp.scm.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新增库存调拨单命令。
 *
 * @author BlueWhale
 */
@Data
public class TransferCreateCommand {

    /** 调拨日期 */
    @NotNull
    private LocalDate transferDate;

    /** 调出仓库 */
    @NotNull
    private Long fromWarehouseId;

    /** 调入仓库 */
    @NotNull
    private Long toWarehouseId;

    /** 备注 */
    private String remark;

    /** 分录 */
    @Valid
    @NotEmpty
    private List<Line> lines;

    /**
     * 调拨分录入参。
     */
    @Data
    public static class Line {

        /** 物料编码 */
        @NotBlank
        private String itemCode;

        /** 调拨数量 */
        @NotNull
        private BigDecimal quantity;

        /** 计量单位 */
        private String uom;
    }
}
